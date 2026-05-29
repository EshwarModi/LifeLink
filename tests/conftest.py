import os
import pytest

# Must be set BEFORE importing app
os.environ['FLASK_ENV']    = 'testing'
os.environ['SECRET_KEY']   = 'test-secret-key-for-ci'
os.environ['DATABASE_URL'] = 'sqlite:///:memory:'

from app import app as _app, db as _db  # noqa: E402


@pytest.fixture(scope='function')
def app():
    _app.config['TESTING']               = True
    _app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///:memory:'
    return _app


@pytest.fixture(scope='function')
def db(app):
    with app.app_context():
        _db.create_all()
        yield _db
        _db.session.remove()
        _db.drop_all()


@pytest.fixture(scope='function')
def client(app, db):
    with app.test_client() as c:
        yield c
