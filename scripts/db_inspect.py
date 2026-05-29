import os, sqlite3
path = 'instance/lifelink.db'
print('cwd=', __import__('os').getcwd())
print('db_path=', path)
print('db_exists=', os.path.exists(path))
if os.path.exists(path):
    conn = sqlite3.connect(path)
    cur = conn.cursor()
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
    print('tables=', cur.fetchall())
    conn.close()
else:
    print('No DB file found at', path)
