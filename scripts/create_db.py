from app import app, db
from sqlalchemy import inspect

with app.app_context():
    db.create_all()
    inspector = inspect(db.engine)
    tables = inspector.get_table_names()
    print('DB URL:', db.engine.url)
    print('Tables created:', tables)
