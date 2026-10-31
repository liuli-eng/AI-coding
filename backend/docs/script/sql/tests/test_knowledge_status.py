#!/usr/bin/env python3
"""Knowledge status migration checks. Writes only to unique disposable databases."""
import unittest
from test_phase1_foundation_rag import MySQL, SQL_DIR

MIGRATION = SQL_DIR / 'update/2026-10-29-knowledge-status.sql'
BEGIN = '-- BEGIN KNOWLEDGE STATUS\n'
END = '-- END KNOWLEDGE STATUS\n'


class KnowledgeStatusMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mysql = MySQL()
        cls.addClassCleanup(cls.mysql.cleanup)

    def test_install_embeds_exact_migration_before_scheduler_selection(self):
        self.assertTrue(MIGRATION.is_file(), 'Knowledge status migration is missing')
        dump = (SQL_DIR / 'ruoyi-ai.sql').read_text()
        self.assertEqual(dump.count(BEGIN), 1)
        self.assertEqual(dump.count(END), 1)
        self.assertEqual(dump.split(BEGIN, 1)[1].split(END, 1)[0], MIGRATION.read_text())
        self.assertLess(dump.index(END), dump.index('\nUSE snail_job;'))

    def test_upgrade_defaults_enabled_and_preserves_disabled_on_repeat(self):
        self.assertTrue(MIGRATION.is_file(), 'Knowledge status migration is missing')
        db = self.mysql.empty('knowledge_upgrade')
        self.mysql.run("CREATE TABLE knowledge_info(id BIGINT PRIMARY KEY,name VARCHAR(50),tenant_id BIGINT NOT NULL DEFAULT 0,share TINYINT,user_id BIGINT); INSERT INTO knowledge_info VALUES(1,'existing',0,1,7);", db)
        self.mysql.run(MIGRATION.read_text(), db)
        self.assertEqual(self.mysql.scalar('SELECT status FROM knowledge_info WHERE id=1;', db), '0')
        self.mysql.run("UPDATE knowledge_info SET status='1' WHERE id=1;", db)
        before = self.mysql.run('SELECT * FROM knowledge_info;', db).stdout
        self.mysql.run(MIGRATION.read_text(), db)
        self.assertEqual(self.mysql.run('SELECT * FROM knowledge_info;', db).stdout, before)
        self.mysql.run("INSERT INTO knowledge_info(id,name) VALUES(2,'new');", db)
        self.assertEqual(self.mysql.scalar('SELECT status FROM knowledge_info WHERE id=2;', db), '0')

    def test_fresh_install_has_status_and_keeps_existing_permissions(self):
        self.assertTrue(MIGRATION.is_file(), 'Knowledge status migration is missing')
        db = self.mysql.base('knowledge_fresh')
        self.assertEqual(self.mysql.scalar("SELECT CONCAT(column_type,':',is_nullable,':',column_default) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='knowledge_info' AND column_name='status';", db), 'char(1):NO:0')
        before = self.mysql.run('SELECT * FROM sys_menu ORDER BY menu_id; SELECT * FROM sys_role_menu ORDER BY role_id,menu_id;', db).stdout
        self.mysql.run(MIGRATION.read_text(), db)
        self.assertEqual(self.mysql.run('SELECT * FROM sys_menu ORDER BY menu_id; SELECT * FROM sys_role_menu ORDER BY role_id,menu_id;', db).stdout, before)


if __name__ == '__main__':
    unittest.main(verbosity=2)
