from app import app


def test_health_endpoint():
    with app.app_context():
        client = app.test_client()
        res = client.get('/health')
        assert res.status_code == 200
        data = res.get_json()
        assert data['status'] == 'ok'
        assert 'db' in data
