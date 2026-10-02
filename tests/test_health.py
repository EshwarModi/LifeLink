"""Health and smoke tests for LifeLink."""
import json
from datetime import datetime, timedelta


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
        'blood_group': 'O+', 'units_needed': 2, 'urgency': 'high',
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
        'urgency':          'high',
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
        'urgency':          'high',
        'reason':           'Emergency surgery',
        'hospital_name':    'City Hospital',
        'hospital_address': '123 Main St, Mumbai',
        'required_by':      '2030-06-01T10:00'
    })
    req_id = res.get_json()['request_id']

    edit_res = client.post(f'/seeker/edit-request/{req_id}', json={
        'blood_group':      'O+',
        'units_needed':     2,
        'urgency':          'high',
        'reason':           'Updated surgery details',
        'hospital_name':    'City Hospital',
        'hospital_address': '123 Main St, Mumbai',
        'required_by':      '2000-01-01T10:00'
    })
    assert edit_res.status_code == 400
    assert edit_res.get_json()['error'] == 'Required by date must be in the future'


def test_accept_match_canonical_endpoint(client):
    _register_donor(client)
    client.get('/logout')
    _register_seeker(client)

    res = client.post('/seeker/create-request', json={
        'blood_group':      'O+',
        'units_needed':     2,
        'urgency':          'critical',
        'reason':           'Emergency surgery',
        'hospital_name':    'City Hospital',
        'hospital_address': '123 Main St, Mumbai',
        'required_by':      '2030-06-01T10:00'
    })
    req_id = res.get_json()['request_id']
    client.get('/logout')

    client.post('/login', json={'username': 'testdonor', 'password': 'Password123'})
    accept_res = client.post(f'/api/accept-match/{req_id}')
    assert accept_res.status_code == 200
    data = accept_res.get_json()
    assert data['success'] is True
    assert 'seeker_phone' in data
    assert 'seeker_name' in data
    assert 'hospital' in data


def test_find_requests_orders_by_urgency_priority(client):
    _register_donor(client)
    client.get('/logout')
    _register_seeker(client)

    # Create requests with different urgency levels
    for urgency in ['low', 'critical', 'medium', 'high']:
        client.post('/seeker/create-request', json={
            'blood_group':      'O+',
            'units_needed':     1,
            'urgency':          urgency,
            'reason':           f'Need blood for {urgency}',
            'hospital_name':    'City Hospital',
            'hospital_address': '123 Main St',
            'required_by':      '2030-12-31T10:00'
        })
    client.get('/logout')

    client.post('/login', json={'username': 'testdonor', 'password': 'Password123'})
    res = client.get('/api/find-requests')
    assert res.status_code == 200
    requests = res.get_json()
    urgencies = [r['urgency'] for r in requests]
    assert urgencies == ['critical', 'high', 'medium', 'low']


def test_auto_match_cascading_tiers(app, db):
    from models import db, User, DonorProfile, SeekerRequest, Match
    from services.matching_service import auto_match_request

    with app.app_context():
        # Create Seeker in Mumbai, Maharashtra
        seeker_u = User(username='seeker_geo', email='seeker_geo@test.com', password='hash', user_type='seeker', full_name='Seeker Geo', phone='1111111111', city='Mumbai', state='Maharashtra')
        db.session.add(seeker_u)
        db.session.commit()

        sr = SeekerRequest(user_id=seeker_u.id, blood_group='B+', units_needed=1, urgency='high', reason='Surgery', hospital_name='Hosp', hospital_address='Addr', required_by=datetime.utcnow() + timedelta(days=1))
        db.session.add(sr)
        db.session.commit()

        # Donor 1: Mumbai, Maharashtra (City match - Tier 1)
        d1_u = User(username='d1', email='d1@test.com', password='hash', user_type='donor', full_name='D1', phone='2222222222', city='Mumbai', state='Maharashtra')
        db.session.add(d1_u)
        db.session.commit()
        d1_p = DonorProfile(user_id=d1_u.id, blood_group='B+', age=25, gender='Male', available_to_donate=True)
        db.session.add(d1_p)

        # Donor 2: Pune, Maharashtra (State match - Tier 2)
        d2_u = User(username='d2', email='d2@test.com', password='hash', user_type='donor', full_name='D2', phone='3333333333', city='Pune', state='Maharashtra')
        db.session.add(d2_u)
        db.session.commit()
        d2_p = DonorProfile(user_id=d2_u.id, blood_group='B+', age=26, gender='Female', available_to_donate=True)
        db.session.add(d2_p)

        # Donor 3: Delhi, Delhi (Nationwide match - Tier 3)
        d3_u = User(username='d3', email='d3@test.com', password='hash', user_type='donor', full_name='D3', phone='4444444444', city='Delhi', state='Delhi')
        db.session.add(d3_u)
        db.session.commit()
        d3_p = DonorProfile(user_id=d3_u.id, blood_group='B+', age=27, gender='Male', available_to_donate=True)
        db.session.add(d3_p)
        db.session.commit()

        # Test cascading fallback with threshold=3: should match all 3 donors across city, state, nationwide
        matched = auto_match_request(sr, threshold=3)
        assert len(matched) == 3
        matched_user_ids = [m.user_id for m in matched]
        assert d1_u.id in matched_user_ids
        assert d2_u.id in matched_user_ids
        assert d3_u.id in matched_user_ids

        # Test with threshold=1: should only match Tier 1 (d1_u in Mumbai)
        Match.query.delete()
        db.session.commit()

        matched_tier1 = auto_match_request(sr, threshold=1)
        assert len(matched_tier1) == 1
        assert matched_tier1[0].user_id == d1_u.id


def test_donor_eligibility_interval(app, db):
    from models import User, DonorProfile, SeekerRequest
    from services.matching_service import auto_match_request
    from services.api_service import find_donors

    with app.app_context():
        # Seeker
        seeker = User(username='seeker_elg', email='seeker_elg@test.com', password='hash', user_type='seeker', full_name='Seeker Elg', phone='1234567890', city='Mumbai', state='Maharashtra')
        db.session.add(seeker)
        db.session.commit()

        sr = SeekerRequest(user_id=seeker.id, blood_group='O+', units_needed=1, urgency='high', reason='Surgery', hospital_name='Hosp', hospital_address='Addr', required_by=datetime.utcnow() + timedelta(days=1))
        db.session.add(sr)
        db.session.commit()

        # Ineligible Donor (donated 10 days ago)
        recent_donor_u = User(username='recent_donor', email='recent@test.com', password='hash', user_type='donor', full_name='Recent Donor', phone='2223334444', city='Mumbai', state='Maharashtra')
        db.session.add(recent_donor_u)
        db.session.commit()
        recent_donor_p = DonorProfile(user_id=recent_donor_u.id, blood_group='O+', age=30, gender='Male', available_to_donate=True, last_donation_date=datetime.utcnow() - timedelta(days=10))
        db.session.add(recent_donor_p)

        # Eligible Donor (donated 100 days ago)
        old_donor_u = User(username='old_donor', email='old@test.com', password='hash', user_type='donor', full_name='Old Donor', phone='5556667777', city='Mumbai', state='Maharashtra')
        db.session.add(old_donor_u)
        db.session.commit()
        old_donor_p = DonorProfile(user_id=old_donor_u.id, blood_group='O+', age=32, gender='Female', available_to_donate=True, last_donation_date=datetime.utcnow() - timedelta(days=100))
        db.session.add(old_donor_p)
        db.session.commit()

        # Test DonorProfile properties
        assert recent_donor_p.is_eligible_to_donate is False
        assert recent_donor_p.next_eligible_date is not None
        assert old_donor_p.is_eligible_to_donate is True

        # Test auto-match excludes ineligible donor
        matched = auto_match_request(sr)
        matched_donor_user_ids = [m.user_id for m in matched]
        assert old_donor_u.id in matched_donor_user_ids
        assert recent_donor_u.id not in matched_donor_user_ids

        # Test find_donors API service excludes ineligible donor
        donors_list, status = find_donors('O+', 'Mumbai')
        assert status == 200
        donor_ids_in_list = [d['id'] for d in donors_list]
        assert old_donor_p.id in donor_ids_in_list
        assert recent_donor_p.id not in donor_ids_in_list



