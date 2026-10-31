#!/usr/bin/env python3
import unittest
from test_phase1_foundation_rag import MySQL, SQL_DIR
MIGRATION=SQL_DIR/'update/2026-10-29-settings-documents.sql'
BEGIN='-- BEGIN SETTINGS DOCUMENTS\n'
END='-- END SETTINGS DOCUMENTS\n'
class SettingsDocumentsMigrationTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.mysql=MySQL();cls.addClassCleanup(cls.mysql.cleanup)
 def test_upgrade_preserves_documents_and_repeat_preserves_choices(self):
  self.assertTrue(MIGRATION.is_file(),'Missing settings/documents migration')
  db=self.mysql.base('settings_upgrade')
  self.mysql.run("DROP TABLE knowledge_settings; ALTER TABLE knowledge_attach DROP INDEX idx_document_workbench,DROP COLUMN file_size,DROP COLUMN business_state; INSERT INTO knowledge_attach(id,knowledge_id,name,type,tenant_id) VALUES(1,42,'a.txt','txt',0);",db)
  self.mysql.run(MIGRATION.read_text(),db)
  self.assertEqual(self.mysql.scalar('SELECT business_state FROM knowledge_attach WHERE id=1',db),'0')
  self.mysql.run("UPDATE knowledge_attach SET business_state='1'; INSERT INTO knowledge_settings(tenant_id,system_name,allowed_types,overwrite_same_name,failure_message,similarity_threshold,version) VALUES('000007','保留名称','[\"txt\"]',1,'保留提示',0.7,3);",db)
  before=self.mysql.run('SELECT * FROM knowledge_attach; SELECT * FROM knowledge_settings;',db).stdout
  self.mysql.run(MIGRATION.read_text(),db)
  self.assertEqual(self.mysql.run('SELECT * FROM knowledge_attach; SELECT * FROM knowledge_settings;',db).stdout,before)
 def test_fresh_install_and_menu_permissions_are_idempotent(self):
  self.assertTrue(MIGRATION.is_file(),'Missing settings/documents migration')
  db=self.mysql.base('settings_fresh')
  self.assertEqual(self.mysql.scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='knowledge_attach' AND column_name IN ('file_size','business_state')",db),'2')
  before=self.mysql.run('SELECT * FROM sys_menu ORDER BY menu_id; SELECT * FROM sys_role_menu ORDER BY role_id,menu_id;',db).stdout
  self.mysql.run(MIGRATION.read_text(),db)
  self.assertEqual(self.mysql.run('SELECT * FROM sys_menu ORDER BY menu_id; SELECT * FROM sys_role_menu ORDER BY role_id,menu_id;',db).stdout,before)
  self.assertEqual(self.mysql.scalar("SELECT COUNT(*) FROM sys_menu WHERE component='knowledge/settings/index'",db),'1')
  self.assertEqual(self.mysql.scalar("SELECT COUNT(*) FROM sys_menu WHERE component='knowledge/attach/upload'",db),'1')
 def test_embedded_migration_precedes_scheduler(self):
  self.assertTrue(MIGRATION.is_file(),'Missing settings/documents migration')
  text=(SQL_DIR/'ruoyi-ai.sql').read_text()
  self.assertEqual(text.count(BEGIN),1)
  self.assertEqual(text.split(BEGIN)[1].split(END)[0],MIGRATION.read_text())
  self.assertLess(text.index(END),text.index('\nUSE snail_job;'))
if __name__=='__main__':unittest.main(verbosity=2)
