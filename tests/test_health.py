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
    assert res.status_code in (200, 301, 302)


def test_csrf_protection_rejects_missing_token(client):
    client.application.config['WTF_CSRF_ENABLED'] = True
    try:
        res = client.post('/login', json={'username': 'test', 'password': 'pass'})
        assert res.status_code == 400
    finally:
        client.application.config['WTF_CSRF_ENABLED'] = False


# ── Auth flow ────────────────────────────────────────────────────────────────

def _register_donor(client):
    return client.post('/register', json={
        'username':    'testdonor',
        'email':       'donor@test.com',
        'password':    'Password123',
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
        'password':  'Password123',
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
    res  = client.post('/login', json={'username': 'testdonor', 'password': 'Password123'})
    data = res.get_json()
    assert res.status_code == 200
    assert data['success'] is True


def test_register_weak_password_rejected(client):
    res = client.post('/register', json={
        'username':    'weakdonor',
        'email':       'weak@test.com',
        'password':    'password123',  # missing uppercase
        'user_type':   'donor',
        'full_name':   'Weak Donor',
        'phone':       '9876543210',
        'city':        'Mumbai',
        'state':       'Maharashtra',
        'blood_group': 'A+',
        'age':         '25',
        'gender':      'Male',
    })
    assert res.status_code == 400
    assert 'Password must be at least 8 characters long' in res.get_json()['error']


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


def test_rate_limiting_login_returns_429(client):
    from app import limiter
    limiter.enabled = True
    client.application.config['RATELIMIT_ENABLED'] = True
    try:
        limiter.reset()
        for _ in range(5):
            res = client.post('/login', json={'username': 'test', 'password': 'WrongPassword1'})
            assert res.status_code == 401

        res6 = client.post('/login', json={'username': 'test', 'password': 'WrongPassword1'})
        assert res6.status_code == 429
        data = res6.get_json()
        assert 'Rate limit exceeded' in data['error']
    finally:
        limiter.enabled = False
        client.application.config['RATELIMIT_ENABLED'] = False


def test_edit_request_rejects_past_date(client):
    _register_seeker(client)
    res = client.post('/seeker/create-request', json={
        'blood_group':      'O+',
        'units_needed':     2,
        'urgency':          'urgent',
        'reason':           'Emergency surgery',
        'hospital_name':    'City Hospital',
        'hospital_address': '123 Main St, Mumbai',
        'required_by':      '2030-06-01T10:00'
    })
    req_id = res.get_json()['request_id']

    edit_res = client.post(f'/seeker/edit-request/{req_id}', json={
        'blood_group':      'O+',
        'units_needed':     2,
        'urgency':          'urgent',
        'reason':           'Updated surgery details',
        'hospital_name':    'City Hospital',
        'hospital_address': '123 Main St, Mumbai',
        'required_by':      '2000-01-01T10:00'
    })
    assert edit_res.status_code == 400
    assert edit_res.get_json()['error'] == 'Required by date must be in the future'
