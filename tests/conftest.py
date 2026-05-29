import os
import pytest

# Must be set before importing app
os.environ['FLASK_ENV']    = 'testing'
os.environ['SECRET_KEY']   = 'test-secret-key-for-ci'
os.environ['DATABASE_URL'] = 'sqlite:///:memory:'

from app import app as flask_app, db as _db


@pytest.fixture(scope='session')
def app():
    flask_app.config['TESTING']              = True
    flask_app.config['WTF_CSRF_ENABLED']     = False
    flask_app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///:memory:'
    return flask_app


@pytest.fixture(scope='function')
def db(app):
    with app.app_context():
        _db.create_all()
        yield _db
        _db.session.remove()
        _db.drop_all()


@pytest.fixture(scope='function')
def client(app, db):
    return app.test_client()
