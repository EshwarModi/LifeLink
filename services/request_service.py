from datetime import datetime
from extensions import db
from models import SeekerRequest
from validators import validate_request
from services.matching_service import auto_match_request


def create_seeker_request(user_id, data):
    if not data:
        return {'error': 'Invalid request data'}, 400

    errors = validate_request(data)
    if errors:
        return {'error': errors[0]}, 400

    try:
        required_by = datetime.fromisoformat(data['required_by'])
    except (ValueError, TypeError):
        return {'error': 'Invalid date format'}, 400

    if required_by <= datetime.utcnow():
        return {'error': 'Required by date must be in the future'}, 400

    sr = SeekerRequest(
        user_id          = user_id,
        blood_group      = data['blood_group'],
        units_needed     = int(data['units_needed']),
        urgency          = data['urgency'],
        reason           = data['reason'].strip(),
        hospital_name    = data['hospital_name'].strip(),
        hospital_address = data['hospital_address'].strip(),
        required_by      = required_by
    )
    db.session.add(sr)
    db.session.commit()

    auto_match_request(sr)

    return {'success': True, 'request_id': sr.id}, 200


def update_request_status(user_id, request_id, data):
    new_status = data.get('status') if data else None
    if new_status not in ('fulfilled', 'cancelled'):
        return {'error': 'Invalid status'}, 400

    sr = SeekerRequest.query.filter_by(id=request_id, user_id=user_id).first()
    if not sr:
        return {'error': 'Request not found'}, 404
    if sr.status != 'open':
        return {'error': 'Only open requests can be updated'}, 400

    sr.status = new_status
    db.session.commit()
    return {'success': True}, 200


def edit_seeker_request(user_id, request_id, data):
    if not data:
        return {'error': 'Invalid request data'}, 400

    sr = SeekerRequest.query.filter_by(id=request_id, user_id=user_id).first()
    if not sr:
        return {'error': 'Request not found'}, 404
    if sr.status != 'open':
        return {'error': 'Only open requests can be edited'}, 400

    errors = validate_request(data)
    if errors:
        return {'error': errors[0]}, 400

    try:
        required_by = datetime.fromisoformat(data['required_by'])
    except (ValueError, TypeError):
        return {'error': 'Invalid date format'}, 400

    if required_by <= datetime.utcnow():
        return {'error': 'Required by date must be in the future'}, 400

    sr.blood_group      = data['blood_group']
    sr.units_needed     = int(data['units_needed'])
    sr.urgency          = data['urgency']
    sr.reason           = data['reason'].strip()
    sr.hospital_name    = data['hospital_name'].strip()
    sr.hospital_address = data['hospital_address'].strip()
    sr.required_by      = required_by
    db.session.commit()
    return {'success': True}, 200
