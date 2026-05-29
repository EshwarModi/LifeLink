import os
import pytest

# Set test environment before importing app
os.environ.setdefault('FLASK_ENV', 'testing')
os.environ.setdefault('SECRET_KEY', 'test-secret-key')
os.environ.setdefault('DATABASE_URL', 'sqlite:///:memory:')

from app import app, db


@pytest.fixture(autouse=True)
def setup_db():
    """Create all tables before each test, drop after."""
    with app.app_context():
        db.create_all()
        yield
        db.session.remove()
        db.drop_all()
