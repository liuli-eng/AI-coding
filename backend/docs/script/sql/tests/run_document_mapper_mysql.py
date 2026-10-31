#!/usr/bin/env python3
"""Runs the actual Java/MyBatis mappers using a temporary DB and restricted test user."""
import os, secrets, subprocess
from pathlib import Path
from test_phase1_foundation_rag import MySQL, REPO
mysql=MySQL();user='doc_test_'+secrets.token_hex(6);password=secrets.token_hex(24)
try:
 db=mysql.base('document_mapper')
 mysql.run(f"CREATE USER '{user}'@'%' IDENTIFIED BY '{password}'; GRANT ALL ON `{db}`.* TO '{user}'@'%';")
 env=os.environ.copy();env['JAVA_HOME']='/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home'
 env.update(DOCUMENTS_TEST_JDBC_URL=f'jdbc:mysql://127.0.0.1:23306/{db}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai',DOCUMENTS_TEST_USER=user,DOCUMENTS_TEST_PASSWORD=password)
 log=REPO/'.superpowers/sdd/2026-10-29-settings-documents/mapper-mysql.log'
 with log.open('w') as out:
  result=subprocess.run(['mvn','-f','backend/pom.xml','-Pdev','-pl','aicoding-modules/aicoding-chat','-am','-Dsurefire.failIfNoSpecifiedTests=false','-DreuseForks=false','-Dtest=KnowledgeDocumentMapperMySQLTest','test'],cwd=REPO,env=env,stdout=out,stderr=subprocess.STDOUT)
 print('Real MyBatis/MySQL integration:', 'PASS' if result.returncode==0 else 'FAIL (see mapper-mysql.log)')
 raise SystemExit(result.returncode)
finally:
 mysql.run(f"DROP USER IF EXISTS '{user}'@'%';",check=False);mysql.cleanup()
