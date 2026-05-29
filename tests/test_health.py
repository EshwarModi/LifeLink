"""Health and smoke tests for LifeLink."""
import json


# ── Health endpoint ──────────────────────────────────────────────────────────

def test_health_endpoint(client):
    res  = client.get('/health')
    data = res.get_json()
    assert res.status_code == 200
    assert data['status'] == 'ok'
    assert data['db']     == 'ok'


# ── Public pages load ────────────────────────────────────────────────────────

def test_home_page(client):
    res = client.get('/home')
    assert res.status_code == 200
    assert b'LifeLink' in res.data


def test_login_page(client):
    res = client.get('/login')
    assert res.status_code == 200
    assert b'Login' in res.data


def test_register_page(client):
    res = client.get('/register')
    assert res.status_code == 200
    assert b'Register' in res.data


def test_root_redirects(client):
    res = client.get('/')
    assert res.status_code in (301, 302)


# ── Auth flow ────────────────────────────────────────────────────────────────

def _register_donor(client):
    return client.post('/register', json={
        'username':    'testdonor',
        'email':       'donor@test.com',
        'password':    'password123',
        'user_type':   'donor',
        'full_name':   'Test Donor',
        'phone':       '9876543210',
        'city':        'Mumbai',
        'state':       'Maharashtra',
        'blood_group': 'O+',
        'age':         '25',
        'gender':      'Male',
    })


def _register_seeker(client):
    return client.post('/register', json={
        'username':  'testseeker',
        'email':     'seeker@test.com',
        'password':  'password123',
        'user_type': 'seeker',
        'full_name': 'Test Seeker',
        'phone':     '9876543211',
        'city':      'Mumbai',
        'state':     'Maharashtra',
    })


def test_donor_registration(client):
    res  = _register_donor(client)
    data = res.get_json()
    assert res.status_code == 200
    assert data['success'] is True


def test_seeker_registration(client):
    res  = _register_seeker(client)
    data = res.get_json()
    assert res.status_code == 200
    assert data['success'] is True


def test_duplicate_username_rejected(client):
    _register_donor(client)
    res  = _register_donor(client)
    data = res.get_json()
    assert res.status_code == 400
    assert 'error' in data


def test_login_success(client):
    _register_donor(client)
    # Log out first (registration auto-logs in)
    client.get('/logout')
    res  = client.post('/login', json={'username': 'testdonor', 'password': 'password123'})
    data = res.get_json()
    assert res.status_code == 200
    assert data['success'] is True


def test_login_wrong_password(client):
    _register_donor(client)
    client.get('/logout')
    res  = client.post('/login', json={'username': 'testdonor', 'password': 'wrongpass'})
    assert res.status_code == 401


def test_dashboard_requires_login(client):
    res = client.get('/dashboard')
    assert res.status_code == 302  # redirect to login


def test_donor_dashboard_accessible(client):
    _register_donor(client)
    res = client.get('/donor/dashboard')
    assert res.status_code == 200


def test_seeker_dashboard_accessible(client):
    _register_seeker(client)
    res = client.get('/seeker/dashboard')
    assert res.status_code == 200


# ── Validation ───────────────────────────────────────────────────────────────

def test_register_invalid_blood_group(client):
    res = client.post('/register', json={
        'username':    'baddonor',
        'email':       'bad@test.com',
        'password':    'password123',
        'user_type':   'donor',
        'full_name':   'Bad Donor',
        'phone':       '9876543210',
        'city':        'Mumbai',
        'state':       'Maharashtra',
        'blood_group': 'Z+',   # invalid
        'age':         '25',
        'gender':      'Male',
    })
    assert res.status_code == 400


def test_register_underage_donor(client):
    res = client.post('/register', json={
        'username':    'youngdonor',
        'email':       'young@test.com',
        'password':    'password123',
        'user_type':   'donor',
        'full_name':   'Young Donor',
        'phone':       '9876543210',
        'city':        'Mumbai',
        'state':       'Maharashtra',
        'blood_group': 'A+',
        'age':         '15',   # under 18
        'gender':      'Male',
    })
    assert res.status_code == 400


def test_create_request_requires_auth(client):
    res = client.post('/seeker/create-request', json={
        'blood_group': 'O+', 'units_needed': 2, 'urgency': 'urgent',
        'reason': 'Surgery', 'hospital_name': 'City Hospital',
        'hospital_address': '123 Main St',
        'required_by': '2030-01-01T10:00'
    })
    assert res.status_code == 401


def test_seeker_can_create_request(client):
    _register_seeker(client)
    res  = client.post('/seeker/create-request', json={
        'blood_group':      'O+',
        'units_needed':     2,
        'urgency':          'urgent',
        'reason':           'Emergency surgery',
        'hospital_name':    'City Hospital',
        'hospital_address': '123 Main St, Mumbai',
        'required_by':      '2030-06-01T10:00'
    })
    data = res.get_json()
    assert res.status_code == 200
    assert data['success'] is True
