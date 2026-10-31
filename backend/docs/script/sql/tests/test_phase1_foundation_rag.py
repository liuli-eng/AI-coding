#!/usr/bin/env python3
"""Real MySQL 8 regression tests; all writes use disposable scratch databases.

Run from any directory: python3 path/to/test_phase1_foundation_rag.py
Requires the running aicoding-assistant-mysql container (override MYSQL_TEST_CONTAINER).
The root password is expanded only inside the container, never returned to the host.
"""

import os
import re
import subprocess
import unittest
import uuid
from pathlib import Path


SQL_DIR = Path(__file__).resolve().parents[1]
REPO = SQL_DIR.parents[3]
MIGRATION = SQL_DIR / "update/2026-10-28-phase1-foundation-rag.sql"
BEGIN = "-- BEGIN PHASE1 FOUNDATION RAG\n"
END = "-- END PHASE1 FOUNDATION RAG\n"
MENU_LOW = 2099102800000000000
MENU_HIGH = 2099102800000000100


class Phase1InstallSynchronizationTest(unittest.TestCase):
    def test_embedded_migration_matches_canonical_sql_before_scheduler_selection(self):
        dump = (SQL_DIR / "ruoyi-ai.sql").read_bytes()
        begin, end = BEGIN.encode(), END.encode()
        self.assertEqual(dump.count(begin), 1, "Keep exactly one BEGIN PHASE1 FOUNDATION RAG marker in ruoyi-ai.sql")
        self.assertEqual(dump.count(end), 1, "Keep exactly one END PHASE1 FOUNDATION RAG marker in ruoyi-ai.sql")
        _, _, remainder = dump.partition(begin)
        embedded, closing_marker, _ = remainder.partition(end)
        self.assertTrue(closing_marker, "Place the Phase 1 END marker after its BEGIN marker in ruoyi-ai.sql")
        self.assertTrue(
            embedded == MIGRATION.read_bytes(),
            "Phase 1 SQL has drifted: copy update/2026-10-28-phase1-foundation-rag.sql "
            "verbatim between the BEGIN/END PHASE1 FOUNDATION RAG markers in ruoyi-ai.sql.",
        )
        scheduler_selection = dump.find(b"\nUSE snail_job;")
        self.assertNotEqual(scheduler_selection, -1, "Cannot find the scheduler database selection; update this guard if its SQL syntax changes")
        self.assertLess(
            dump.index(end) + len(end), scheduler_selection,
            "Move the entire marked Phase 1 block before USE snail_job; so it runs in the application database.",
        )


class MySQL:
    def __init__(self):
        self.container = os.environ.get("MYSQL_TEST_CONTAINER", "aicoding-assistant-mysql")
        self.prefix = "aicoding_phase1_test_" + uuid.uuid4().hex[:12]
        self.databases = set()

    def run(self, sql, database=None, check=True):
        if database is not None and database not in self.databases:
            raise AssertionError("Refusing a database outside this test run")
        # Do not pass passwords as argv, read docker inspect, or print SQL/credentials.
        command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4'
        if database:
            command += " " + database
        result = subprocess.run(
            ["docker", "exec", "-i", self.container, "sh", "-c", command],
            input=sql, text=True, capture_output=True, check=False,
        )
        if check and result.returncode:
            # Dumps can contain secrets: report only numeric error codes, not SQL excerpts.
            codes = re.findall(r"ERROR (\d+)", result.stderr)
            raise AssertionError(f"MySQL failed (exit {result.returncode}, error codes {codes})")
        return result

    def scalar(self, sql, database):
        rows = self.run(sql, database).stdout.strip().splitlines()
        return rows[-1] if rows else ""

    def base(self, label, before_phase1=False, before_materials=False):
        database = self.prefix + "_" + label
        job_database = database + "_job"
        self.databases.update((database, job_database))
        sql = (SQL_DIR / "ruoyi-ai.sql").read_text()
        if before_phase1 and BEGIN in sql:
            start, remainder = sql.split(BEGIN, 1)
            _, finish = remainder.split(END, 1)
            sql = start + finish
        if before_phase1 or before_materials:
            material_begin, material_end = "-- BEGIN COURSE MATERIALS\n", "-- END COURSE MATERIALS\n"
            if material_begin in sql:
                start, remainder = sql.split(material_begin, 1)
                _, finish = remainder.split(material_end, 1)
                sql = start + finish
        sql = sql.replace("`aicoding-assistant`", f"`{database}`")
        sql = re.sub(r"\bsnail_job\b", job_database, sql)
        # Fail closed before sending a full dump that could select a live database.
        selectors = re.findall(
            r"(?im)^\s*(?:USE|(?:CREATE|DROP)\s+DATABASE(?:\s+IF\s+(?:NOT\s+)?EXISTS)?)\s+`?([\w-]+)`?", sql,
        )
        if not selectors or set(selectors) != {database, job_database}:
            raise AssertionError("Full-install SQL contains an unsafe database selector")
        self.run(sql)
        return database

    def empty(self, label):
        database = self.prefix + "_" + label
        self.databases.add(database)
        self.run(f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;")
        return database

    def cleanup(self):
        for database in sorted(self.databases):
            if not database.startswith(self.prefix + "_"):
                raise AssertionError("Refusing unsafe cleanup")
            self.run(f"DROP DATABASE IF EXISTS `{database}`;")


class Phase1FoundationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mysql = MySQL()
        cls.addClassCleanup(cls.mysql.cleanup)
        version = cls.mysql.run("SELECT VERSION();").stdout.strip()
        if not version.startswith("8."):
            raise AssertionError("These tests require MySQL 8")
        cls.fresh = cls.mysql.base("fresh")
        cls.upgrade = cls.mysql.base("upgrade", before_phase1=True)
        cls.mysql.run("""
            INSERT INTO chat_session(id,user_id,session_title,session_content,tenant_id)
            VALUES (919001,919001,'Existing session','Existing content',0);
            INSERT INTO sys_role(role_id,tenant_id,role_name,role_key,role_sort,status)
            VALUES (919002,'000000','Custom role','custom_operator',99,'0'),
                   (919004,'000000','Existing admin','admin',99,'0');
            INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,menu_type,perms)
            VALUES (919003,'Custom menu',0,'custom-report','C','custom:report:list');
            INSERT INTO sys_role_menu(role_id,menu_id) VALUES (919002,919003),(919004,919003);
        """, cls.upgrade)
        cls.roles_before = cls.mysql.run("SELECT * FROM sys_role ORDER BY role_id;", cls.upgrade).stdout
        cls.assignments_before = cls.mysql.run("SELECT * FROM sys_role_menu ORDER BY role_id,menu_id;", cls.upgrade).stdout
        if MIGRATION.is_file():
            cls.mysql.run(MIGRATION.read_text(), cls.upgrade)
            cls.mysql.run(MIGRATION.read_text(), cls.upgrade)
        materials_migration = SQL_DIR / "update/2026-10-28-course-materials.sql"
        if materials_migration.is_file():
            cls.mysql.run(materials_migration.read_text(), cls.upgrade)

    def require_migration(self):
        self.assertTrue(MIGRATION.is_file(), "The Phase 1 upgrade migration is missing")

    def test_00_upgrade_migration_is_available(self):
        self.require_migration()

    def assert_entity_columns(self, database):
        sources = {
            "chat_question": "chat/ChatQuestion.java", "chat_prompt": "chat/ChatPrompt.java",
            "learning_course": "learning/LearningCourse.java", "course_enrollment": "learning/CourseEnrollment.java",
            "chat_session": "chat/ChatSession.java",
        }
        entity_root = REPO / "backend/aicoding-modules/aicoding-chat/src/main/java/org/ruoyi/domain/entity"
        audit = {"create_dept", "create_by", "create_time", "update_by", "update_time"}
        for table, source in sources.items():
            with self.subTest(database=database, table=table):
                text = (entity_root / source).read_text()
                names = re.findall(r"private (?:Long|Integer|String|Date) (\w+)\s*;", text)
                expected = {re.sub(r"([A-Z])", r"_\1", name).lower() for name in names}
                expected |= audit | {"tenant_id"}
                if table == "chat_session":
                    expected.add("knowledge_id")
                actual = set(self.mysql.run(
                    f"SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='{table}';",
                    database,
                ).stdout.splitlines())
                self.assertTrue(expected <= actual, f"{table} missing persisted fields: {sorted(expected - actual)}")
        knowledge = self.mysql.scalar("""
            SELECT CONCAT(column_type,':',is_nullable) FROM information_schema.columns
            WHERE table_schema=DATABASE() AND table_name='chat_session' AND column_name='knowledge_id';
        """, database)
        self.assertEqual(knowledge, "varchar(64):YES")

    def test_10_fresh_install_maps_all_entity_fields(self):
        self.assert_entity_columns(self.fresh)

    def test_20_repeat_upgrade_preserves_existing_data_and_assignments(self):
        self.require_migration()
        self.assert_entity_columns(self.upgrade)
        self.assertEqual(self.mysql.scalar("SELECT CONCAT(session_title,'|',session_content) FROM chat_session WHERE id=919001;", self.upgrade), "Existing session|Existing content")
        self.assertEqual(self.mysql.scalar("SELECT COUNT(*) FROM chat_session WHERE id=919001 AND knowledge_id IS NULL;", self.upgrade), "1")
        self.assertEqual(self.mysql.run("SELECT * FROM sys_role ORDER BY role_id;", self.upgrade).stdout, self.roles_before)
        actual = set(self.mysql.run("SELECT * FROM sys_role_menu ORDER BY role_id,menu_id;", self.upgrade).stdout.splitlines())
        self.assertTrue(set(self.assignments_before.splitlines()) <= actual)
        self.assertEqual(self.mysql.scalar("SELECT path FROM sys_menu WHERE menu_id=919003;", self.upgrade), "custom-report")

    def test_30_admin_routes_and_controller_permissions_exist_without_user_grants(self):
        self.require_migration()
        routes = {
            "chat/question/index", "chat/question/exceptions", "chat/prompt/index",
            "learning/course/index", "dashboard/analytics/index",
        }
        controller_root = REPO / "backend/aicoding-modules/aicoding-chat/src/main/java/org/ruoyi/controller"
        permissions = set()
        for source in ["chat/ChatQuestionController.java", "chat/ChatPromptController.java", "learning/LearningCourseController.java"]:
            permissions |= set(re.findall(r'@SaCheckPermission\("([^"]+)"\)', (controller_root / source).read_text()))
        for database in [self.fresh, self.upgrade]:
            with self.subTest(database=database):
                actual = set(self.mysql.run(f"SELECT perms FROM sys_menu WHERE menu_id > {MENU_LOW} AND menu_id < {MENU_HIGH};", database).stdout.splitlines())
                self.assertTrue(permissions <= actual, f"Missing controller permissions: {sorted(permissions - actual)}")
                components = set(self.mysql.run(f"SELECT component FROM sys_menu WHERE menu_id > {MENU_LOW} AND menu_id < {MENU_HIGH} AND menu_type='C' AND status='0' AND visible='0';", database).stdout.splitlines())
                self.assertEqual(components, routes)
                for component in components:
                    self.assertTrue((REPO / "admin/apps/web-antd/src/views" / (component + ".vue")).is_file())
                self.assertEqual(self.mysql.scalar(f"""
                    SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.role_id=rm.role_id
                    WHERE rm.menu_id > {MENU_LOW} AND rm.menu_id < {MENU_HIGH} AND r.role_key NOT IN ('admin','superadmin');
                """, database), "0")
                self.assertEqual(self.mysql.scalar(f"""
                    SELECT COUNT(*) FROM sys_role r CROSS JOIN sys_menu m
                    LEFT JOIN sys_role_menu rm ON rm.role_id=r.role_id AND rm.menu_id=m.menu_id
                    WHERE r.role_key IN ('admin','superadmin') AND r.status='0' AND r.del_flag='0'
                    AND m.menu_id > {MENU_LOW} AND m.menu_id < {MENU_HIGH} AND rm.role_id IS NULL;
                """, database), "0")

    def test_40_snapshots_progress_and_tenant_enrollment_uniqueness_survive_repeat_upgrade(self):
        self.require_migration()
        database = self.upgrade
        self.mysql.run("""
            INSERT INTO chat_prompt(id,tenant_id,name,chat_mode,content,status,version)
            VALUES (919010,'000000','Stored prompt','learning','Immutable prompt','enabled',7);
            INSERT INTO learning_course(id,tenant_id,title,chapters,status,version)
            VALUES (919011,'000000','Stored course','[]','published',4);
            INSERT INTO course_enrollment(id,tenant_id,course_id,user_id,title,chapters,course_version,completed_chapters,chapter_sessions,version)
            VALUES (919012,'000000',919011,919001,'Course snapshot','[]',4,'["chapter-one"]','{"chapter-one":"919001"}',3),
                   (919013,'123456',919011,919001,'Other tenant','[]',4,'[]','{}',1);
            INSERT INTO chat_question(id,tenant_id,session_id,user_id,question,answer,answer_status,retrieval_status,review_status,review_note,version)
            VALUES (919014,'000000',919001,919001,'Stored question','Stored answer','success','matched','reviewed','Keep review',2);
            UPDATE chat_session SET chat_mode='learning',prompt_id=919010,prompt_name='Stored prompt',prompt_version=7,
                prompt_snapshot='Immutable prompt',learning_context='Stored chapter',knowledge_id='kb-string-identifier' WHERE id=919001;
        """, database)
        before = self.mysql.run("SELECT * FROM chat_prompt WHERE id=919010; SELECT * FROM learning_course WHERE id=919011; SELECT * FROM course_enrollment WHERE id IN (919012,919013) ORDER BY id; SELECT * FROM chat_question WHERE id=919014; SELECT * FROM chat_session WHERE id=919001;", database).stdout
        duplicate = self.mysql.run("""
            INSERT INTO course_enrollment(id,tenant_id,course_id,user_id,title,chapters)
            VALUES (919015,'000000',919011,919001,'Duplicate','[]');
        """, database, check=False)
        self.assertNotEqual(duplicate.returncode, 0, "Concurrent joins must be stopped by a tenant/user/course unique key")
        self.assertIn("1062", duplicate.stderr)
        self.mysql.run(MIGRATION.read_text(), database)
        after = self.mysql.run("SELECT * FROM chat_prompt WHERE id=919010; SELECT * FROM learning_course WHERE id=919011; SELECT * FROM course_enrollment WHERE id IN (919012,919013) ORDER BY id; SELECT * FROM chat_question WHERE id=919014; SELECT * FROM chat_session WHERE id=919001;", database).stdout
        self.assertEqual(after, before)

    def test_50_reserved_menu_collision_fails_before_schema_changes(self):
        self.require_migration()
        database = self.mysql.empty("collision")
        self.mysql.run(f"""
            CREATE TABLE sys_menu LIKE `{self.upgrade}`.sys_menu;
            CREATE TABLE sys_role LIKE `{self.upgrade}`.sys_role;
            CREATE TABLE sys_role_menu LIKE `{self.upgrade}`.sys_role_menu;
            CREATE TABLE chat_session LIKE `{self.upgrade}`.chat_session;
            INSERT INTO sys_menu(menu_id,menu_name,perms) VALUES (2099102800000000001,'Existing unrelated menu','custom:collision');
        """, database)
        result = self.mysql.run(MIGRATION.read_text(), database, check=False)
        self.assertNotEqual(result.returncode, 0, "Reserved ID collisions must stop, never silently grant an unrelated menu")
        self.assertEqual(self.mysql.scalar("SELECT perms FROM sys_menu WHERE menu_id=2099102800000000001;", database), "custom:collision")
        self.assertEqual(self.mysql.scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('chat_question','chat_prompt','learning_course','course_enrollment');", database), "0")

    def test_60_legacy_duplicates_merge_without_losing_grants_or_custom_children(self):
        database = self.mysql.base("duplicate_menus", before_phase1=True)
        self.mysql.run("""
            INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,component,menu_type,perms) VALUES
            (2099010100000000026,'问答记录',2000209300188356609,'questions','chat/question/index','C','chat:question:list'),
            (2099010100000000027,'问答详情',2099010100000000026,'#','','F','chat:question:query'),
            (2099010100000000028,'问答复核',2099010100000000026,'#','','F','chat:question:review'),
            (2099010100000000029,'异常问题排查',2000209300188356609,'exceptions','chat/question/exceptions','C','chat:question:exceptions'),
            (2099010100000000030,'业务数据总览',2000209300188356609,'overview','dashboard/analytics/index','C','chat:overview:query'),
            (2099010100000000031,'Prompt 管理',2000209300188356609,'prompts','chat/prompt/index','C','chat:prompt:list'),
            (2099010100000000032,'Prompt查询',2099010100000000031,'#','','F','chat:prompt:query'),
            (2099010100000000033,'Prompt新增',2099010100000000031,'#','','F','chat:prompt:add'),
            (2099010100000000034,'Prompt修改',2099010100000000031,'#','','F','chat:prompt:edit'),
            (2099010100000000035,'Prompt删除',2099010100000000031,'#','','F','chat:prompt:remove'),
            (2099010100000000036,'课程管理',2000209300188356609,'courses','learning/course/index','C','learning:course:list'),
            (2099010100000000037,'课程查询',2099010100000000036,'#','','F','learning:course:query'),
            (2099010100000000038,'课程新增',2099010100000000036,'#','','F','learning:course:add'),
            (2099010100000000039,'课程修改',2099010100000000036,'#','','F','learning:course:edit'),
            (919080,'Custom export',2099010100000000031,'#','','F','custom:prompt:export');
            INSERT INTO sys_role(role_id,tenant_id,role_name,role_key,role_sort,status)
            VALUES (919081,'000000','Reviewer','custom_reviewer',99,'0');
            INSERT INTO sys_role_menu(role_id,menu_id)
            SELECT 919081,menu_id FROM sys_menu
            WHERE menu_id BETWEEN 2099010100000000026 AND 2099010100000000039 OR menu_id=919080;
        """, database)
        roles = self.mysql.run("SELECT * FROM sys_role ORDER BY role_id;", database).stdout
        configs = self.mysql.run("SELECT * FROM sys_config ORDER BY config_id;", database).stdout
        self.mysql.run(MIGRATION.read_text(), database)
        self.assertEqual(self.mysql.scalar("SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 2099010100000000026 AND 2099010100000000039;", database), "0", "Remove legacy page/button duplicates once canonical entries exist")
        counts = self.mysql.run("""
            SELECT component,COUNT(*) FROM sys_menu WHERE menu_type='C' AND component IN
            ('chat/question/index','chat/question/exceptions','chat/prompt/index','learning/course/index','dashboard/analytics/index')
            GROUP BY component;
        """, database).stdout.splitlines()
        self.assertEqual(len(counts), 5)
        self.assertTrue(all(row.endswith("\t1") for row in counts))
        self.assertEqual(self.mysql.scalar(f"SELECT COUNT(*) FROM sys_role_menu WHERE role_id=919081 AND menu_id>{MENU_LOW} AND menu_id<{MENU_HIGH};", database), "14", "Transfer both page and button grants to their surviving entries")
        self.assertEqual(self.mysql.scalar("SELECT parent_id FROM sys_menu WHERE menu_id=919080;", database), "2099102800000000020")
        self.assertEqual(self.mysql.scalar("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=919081 AND menu_id=919080;", database), "1")
        before = self.mysql.run("SELECT * FROM sys_menu ORDER BY menu_id; SELECT * FROM sys_role_menu ORDER BY role_id,menu_id;", database).stdout
        self.mysql.run(MIGRATION.read_text(), database)
        self.assertEqual(self.mysql.run("SELECT * FROM sys_menu ORDER BY menu_id; SELECT * FROM sys_role_menu ORDER BY role_id,menu_id;", database).stdout, before)
        self.assertEqual(self.mysql.run("SELECT * FROM sys_role ORDER BY role_id;", database).stdout, roles)
        self.assertEqual(self.mysql.run("SELECT * FROM sys_config ORDER BY config_id;", database).stdout, configs)

    def test_70_legacy_id_with_unrelated_identity_is_preserved(self):
        database = self.mysql.base("legacy_collision", before_phase1=True)
        self.mysql.run("""
            INSERT INTO sys_menu(menu_id,menu_name,parent_id,path,component,menu_type,perms)
            VALUES (2099010100000000030,'Custom report',0,'custom-report','custom/report/index','C','custom:report:list');
            INSERT INTO sys_role_menu(role_id,menu_id) VALUES (1,2099010100000000030);
        """, database)
        self.mysql.run(MIGRATION.read_text(), database)
        self.assertEqual(self.mysql.scalar("SELECT perms FROM sys_menu WHERE menu_id=2099010100000000030;", database), "custom:report:list")
        self.assertEqual(self.mysql.scalar("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=1 AND menu_id=2099010100000000030;", database), "1")


if __name__ == "__main__":
    unittest.main(verbosity=2)
