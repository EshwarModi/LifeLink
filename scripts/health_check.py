from app import app

with app.test_client() as c:
    resp = c.get('/health')
    print('STATUS:', resp.status_code)
    try:
        print('JSON:', resp.get_json())
    except Exception:
        print('No JSON response')
