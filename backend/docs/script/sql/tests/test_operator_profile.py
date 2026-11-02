#!/usr/bin/env python3
"""Operator override migration checks.

Writes only to unique disposable databases. Requires a reachable MySQL 8 with the
`mysql` CLI on PATH (the same harness used by the other SQL checks); every test
skips instead of failing when that harness is unavailable.
"""
import unittest

from test_phase1_foundation_rag import MySQL, SQL_DIR

MIGRATION = SQL_DIR / 'update/2026-11-02-operator-profile.sql'

EXPECTED_TABLES = [
    'operator_profile',
    'operator_ruleset',
    'operator_audit',
    'operator_refusal_stat',
]


class OperatorProfileMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        try:
            cls.mysql = MySQL()
        except Exception as error:  # noqa: BLE001 - harness absence is a skip, not a failure
            raise unittest.SkipTest(f'mysql CLI harness unavailable: {error}')
        cls.addClassCleanup(cls.mysql.cleanup)

    def require_migration(self):
        if not MIGRATION.is_file():
            self.fail(f'missing migration: {MIGRATION}')

    def test_tables_created_once_and_repeat_is_idempotent(self):
        self.require_migration()
        db = self.mysql.empty('operator_tables')
        self.mysql.run(MIGRATION.read_text(), db)

        names = self.mysql.scalar(
            "SELECT GROUP_CONCAT(table_name ORDER BY table_name) FROM information_schema.tables "
            "WHERE table_schema = DATABASE() AND table_name LIKE 'operator\\_%';", db)
        self.assertEqual(sorted(names.split(',')), sorted(EXPECTED_TABLES))

        # 第二次执行不得报错、不得改变已有结构与数据
        before = self.mysql.run(
            "CHECKSUM TABLE " + ', '.join(f'`{name}`' for name in EXPECTED_TABLES) + ';', db).stdout
        self.mysql.run(MIGRATION.read_text(), db)
        after = self.mysql.run(
            "CHECKSUM TABLE " + ', '.join(f'`{name}`' for name in EXPECTED_TABLES) + ';', db).stdout
        self.assertEqual(before, after)

    def test_chat_session_columns_added_and_preserved(self):
        self.require_migration()
        db = self.mysql.empty('operator_session')
        self.mysql.run(
            "CREATE TABLE chat_session(id BIGINT PRIMARY KEY, chat_mode VARCHAR(20), note VARCHAR(20));"
            "INSERT INTO chat_session VALUES(1, 'programming', 'keep');", db)
        self.mysql.run(MIGRATION.read_text(), db)

        for column in ('operator_profile_id', 'operator_inject_hash'):
            count = self.mysql.scalar(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                f"AND table_name = 'chat_session' AND column_name = '{column}';", db)
            self.assertEqual(count, '1', f'{column} was not added')

        self.assertEqual(
            self.mysql.scalar("SELECT note FROM chat_session WHERE id = 1;", db), 'keep')

        # 重复执行不得报 duplicate column
        self.mysql.run(MIGRATION.read_text(), db)

    def test_menus_and_role_grants_are_idempotent(self):
        self.require_migration()
        db = self.mysql.empty('operator_menus')
        self.mysql.run(
            "CREATE TABLE sys_menu(menu_id BIGINT PRIMARY KEY, menu_name VARCHAR(50));"
            "CREATE TABLE sys_role_menu(role_id BIGINT, menu_id BIGINT, PRIMARY KEY(role_id, menu_id));"
            "CREATE TABLE sys_role(role_id BIGINT PRIMARY KEY, role_key VARCHAR(50),"
            " status CHAR(1), del_flag CHAR(1));"
            "INSERT INTO sys_role VALUES(1,'superadmin','0','0'),(2,'admin','0','0'),"
            "(3,'user','0','0'),(4,'admin','0','2');", db)

        self.mysql.run(MIGRATION.read_text(), db)
        first = self.mysql.run(
            'SELECT menu_id FROM sys_menu ORDER BY menu_id;'
            'SELECT role_id, menu_id FROM sys_role_menu ORDER BY role_id, menu_id;', db).stdout
        self.mysql.run(MIGRATION.read_text(), db)
        second = self.mysql.run(
            'SELECT menu_id FROM sys_menu ORDER BY menu_id;'
            'SELECT role_id, menu_id FROM sys_role_menu ORDER BY role_id, menu_id;', db).stdout
        self.assertEqual(first, second)

        # 普通用户与已删除角色不得获得新增管理菜单
        leaked = self.mysql.scalar(
            'SELECT COUNT(*) FROM sys_role_menu WHERE role_id IN (3, 4);', db)
        self.assertEqual(leaked, '0')


if __name__ == '__main__':
    unittest.main(verbosity=2)
