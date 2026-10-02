from datetime import datetime
from extensions import db
from models import User, DonorProfile, SeekerRequest
from validators import VALID_BLOOD_GROUPS


from sqlalchemy import case

def get_requests_for_donor(donor_user_id):
    dp = DonorProfile.query.filter_by(user_id=donor_user_id).first()
    if not dp:
        return {'error': 'Donor profile not found'}, 404

    urgency_priority = case(
        (SeekerRequest.urgency == 'critical', 1),
        (SeekerRequest.urgency == 'high', 2),
        (SeekerRequest.urgency == 'medium', 3),
        (SeekerRequest.urgency == 'low', 4),
        else_=5
    )

    open_requests = SeekerRequest.query.filter(
        SeekerRequest.blood_group == dp.blood_group,
        SeekerRequest.status == 'open'
    ).order_by(urgency_priority.asc(), SeekerRequest.required_by.asc()).all()

    data = [{
        'id':            r.id,
        'blood_group':   r.blood_group,
        'units_needed':  r.units_needed,
        'urgency':       r.urgency,
        'hospital_name': r.hospital_name,
        'required_by':   r.required_by.isoformat(),
        'user': {
            'full_name': r.user.full_name,
            'city':      r.user.city,
        }
    } for r in open_requests]

    return data, 200


def update_donor_availability(donor_user_id, data):
    if not data:
        return {'error': 'Invalid request data'}, 400

    dp = DonorProfile.query.filter_by(user_id=donor_user_id).first()
    if not dp:
        return {'error': 'Donor profile not found'}, 404

    if 'available_to_donate' in data:
        dp.available_to_donate = bool(data['available_to_donate'])
    if data.get('last_donation_date'):
        try:
            dp.last_donation_date = datetime.fromisoformat(data['last_donation_date'])
        except ValueError:
            return {'error': 'Invalid date format'}, 400
    if 'medical_conditions' in data:
        dp.medical_conditions = data['medical_conditions']

    db.session.commit()
    return {'success': True}, 200


def find_donors(blood_group, city):
    if blood_group not in VALID_BLOOD_GROUPS:
        return {'error': 'Invalid blood group'}, 400

    q = DonorProfile.query.filter_by(blood_group=blood_group, available_to_donate=True)
    if city:
        q = q.join(User).filter(User.city.ilike(f'%{city}%'))

    data = [{
        'id':          d.id,
        'blood_group': d.blood_group,
        'age':         d.age,
        'user': {
            'full_name': d.user.full_name,
            'city':      d.user.city,
            'state':     d.user.state,
        }
    } for d in q.all()]

    return data, 200
