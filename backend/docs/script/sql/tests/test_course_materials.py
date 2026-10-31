#!/usr/bin/env python3
"""Course-material migration regression. All database writes are disposable scratch only."""
import unittest
import subprocess
import select
from concurrent.futures import ThreadPoolExecutor, TimeoutError
from test_phase1_foundation_rag import MySQL, SQL_DIR

MIGRATION = SQL_DIR / 'update/2026-10-28-course-materials.sql'
BEGIN = '-- BEGIN COURSE MATERIALS\n'
END = '-- END COURSE MATERIALS\n'

class MaterialInstallSynchronizationTest(unittest.TestCase):
    def test_exact_migration_embedded_before_scheduler(self):
        self.assertTrue(MIGRATION.is_file(), 'Add the course materials migration')
        dump=(SQL_DIR/'ruoyi-ai.sql').read_bytes()
        self.assertEqual(dump.count(BEGIN.encode()),1)
        self.assertEqual(dump.count(END.encode()),1)
        embedded=dump.split(BEGIN.encode(),1)[1].split(END.encode(),1)[0]
        self.assertEqual(embedded,MIGRATION.read_bytes())
        self.assertLess(dump.index(END.encode()),dump.index(b'\nUSE snail_job;'))

class CourseMaterialsMySQLTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mysql=MySQL();cls.addClassCleanup(cls.mysql.cleanup)
        cls.fresh=cls.mysql.base('materials_fresh')
        cls.upgrade=cls.mysql.base('materials_upgrade',before_materials=True)
        cls.mysql.run("""
            INSERT INTO learning_course(id,tenant_id,title,chapters,status,version)
            VALUES(929001,'000000','Existing course','[]','published',2);
            INSERT INTO course_enrollment(id,tenant_id,course_id,user_id,title,chapters,version)
            VALUES(929002,'000000',929001,1,'Snapshot','[]',3);
            INSERT INTO chat_session(id,user_id,session_title,tenant_id,learning_context,knowledge_id)
            VALUES(929003,1,'Existing chat',0,'Immutable context',NULL);
        """,cls.upgrade)
        cls.old=cls.mysql.run('SELECT * FROM sys_menu ORDER BY menu_id;SELECT * FROM sys_role_menu ORDER BY role_id,menu_id;SELECT * FROM sys_config ORDER BY config_id;',cls.upgrade).stdout
        if MIGRATION.is_file():
            cls.mysql.run(MIGRATION.read_text(),cls.upgrade);cls.mysql.run(MIGRATION.read_text(),cls.upgrade)
    def test_10_fresh_and_repeated_upgrade_have_all_columns(self):
        self.assertTrue(MIGRATION.is_file())
        for database in [self.fresh,self.upgrade]:
            for table,column in [('learning_course','material_ids'),('course_enrollment','material_ids'),('chat_session','course_material_ids')]:
                self.assertEqual(self.mysql.scalar(f"SELECT CONCAT(data_type,':',is_nullable) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='{table}' AND column_name='{column}';",database),'longtext:YES')
            columns=set(self.mysql.run("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_course_material';",database).stdout.splitlines())
            self.assertTrue({'id','tenant_id','oss_id','name','size','suffix','text_content','create_dept','create_by','create_time','update_by','update_time'}<=columns)
    def test_20_upgrade_preserves_old_values_null_snapshots_and_permissions(self):
        self.assertTrue(MIGRATION.is_file())
        database=self.upgrade
        self.assertEqual(self.mysql.scalar("SELECT CONCAT(title,':',version,':',material_ids IS NULL) FROM learning_course WHERE id=929001;",database),'Existing course:2:1')
        self.assertEqual(self.mysql.scalar("SELECT CONCAT(title,':',version,':',material_ids IS NULL) FROM course_enrollment WHERE id=929002;",database),'Snapshot:3:1')
        self.assertEqual(self.mysql.scalar("SELECT CONCAT(learning_context,':',course_material_ids IS NULL,':',knowledge_id IS NULL) FROM chat_session WHERE id=929003;",database),'Immutable context:1:1')
        self.assertEqual(self.mysql.run('SELECT * FROM sys_menu ORDER BY menu_id;SELECT * FROM sys_role_menu ORDER BY role_id,menu_id;SELECT * FROM sys_config ORDER BY config_id;',database).stdout,self.old)
    def test_30_snapshot_reference_semantics_and_repeat_preservation(self):
        self.assertTrue(MIGRATION.is_file())
        database=self.upgrade
        self.mysql.run("""
            INSERT INTO learning_course_material(id,tenant_id,oss_id,name,size,suffix,text_content,create_by)
            VALUES(929004,'000000',929005,'material.txt',8,'txt','全文',1);
            UPDATE learning_course SET material_ids='["929004"]' WHERE id=929001;
            UPDATE course_enrollment SET material_ids='["929004"]' WHERE id=929002;
            UPDATE chat_session SET course_material_ids='["929004"]' WHERE id=929003;
        """,database)
        self.assertEqual(self.mysql.scalar("SELECT (SELECT COUNT(*) FROM learning_course WHERE JSON_CONTAINS(COALESCE(material_ids,'[]'),JSON_QUOTE('929004'))) + (SELECT COUNT(*) FROM course_enrollment WHERE JSON_CONTAINS(COALESCE(material_ids,'[]'),JSON_QUOTE('929004'))) + (SELECT COUNT(*) FROM chat_session WHERE JSON_CONTAINS(COALESCE(course_material_ids,'[]'),JSON_QUOTE('929004')));",database),'3')
        before=self.mysql.run('SELECT * FROM learning_course_material;SELECT * FROM learning_course WHERE id=929001;SELECT * FROM course_enrollment WHERE id=929002;SELECT * FROM chat_session WHERE id=929003;',database).stdout
        self.mysql.run(MIGRATION.read_text(),database)
        self.assertEqual(self.mysql.run('SELECT * FROM learning_course_material;SELECT * FROM learning_course WHERE id=929001;SELECT * FROM course_enrollment WHERE id=929002;SELECT * FROM chat_session WHERE id=929003;',database).stdout,before)
        self.assertEqual(self.mysql.scalar("SELECT JSON_CONTAINS(material_ids,JSON_QUOTE('929004')) FROM learning_course WHERE id=929001;",database),'1')
        self.assertEqual(self.mysql.scalar("SELECT JSON_CONTAINS(material_ids,JSON_QUOTE('92900')) FROM learning_course WHERE id=929001;",database),'0')


    def test_40_material_row_lock_serializes_binding_before_deletion_reference_check(self):
        database=self.upgrade
        self.mysql.run("INSERT INTO learning_course_material(id,tenant_id,oss_id,name,size,suffix,text_content,create_by) VALUES(929010,'000000',929011,'race.txt',4,'txt','text',1);",database)
        command='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names --unbuffered --default-character-set=utf8mb4 '+database
        binder=subprocess.Popen(['docker','exec','-i',self.mysql.container,'sh','-c',command],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,bufsize=1)
        try:
            binder.stdin.write("SET TRANSACTION ISOLATION LEVEL READ COMMITTED;START TRANSACTION;SELECT id FROM learning_course_material WHERE id=929010 FOR UPDATE;\n");binder.stdin.flush()
            self.assertTrue(select.select([binder.stdout],[],[],10)[0], 'Timed out acquiring the scratch material row lock')
            self.assertEqual(binder.stdout.readline().strip(),'929010')
            binder.stdin.write("UPDATE learning_course SET material_ids='[\"929010\"]' WHERE id=929001;\n");binder.stdin.flush()
            with ThreadPoolExecutor(max_workers=1) as executor:
                deleter=executor.submit(self.mysql.run,"SET TRANSACTION ISOLATION LEVEL READ COMMITTED;START TRANSACTION;SELECT id FROM learning_course_material WHERE id=929010 FOR UPDATE;SELECT COUNT(*) FROM learning_course WHERE JSON_CONTAINS(COALESCE(material_ids,'[]'),JSON_QUOTE('929010'));COMMIT;",database)
                try:
                    with self.assertRaises(TimeoutError):deleter.result(timeout=0.3)
                finally:
                    binder.stdin.write('COMMIT;\n');binder.stdin.flush()
                self.assertEqual(deleter.result(timeout=10).stdout.strip().splitlines(),['929010','1'])
        finally:
            binder.stdin.close()
            binder.wait(timeout=10)
            binder.stdout.close();binder.stderr.close()

if __name__=='__main__':unittest.main(verbosity=2)
