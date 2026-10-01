from extensions import db
from models import DonorProfile, SeekerRequest, Match


def auto_match_request(seeker_request):
    matching_donors = DonorProfile.query.filter_by(
        blood_group=seeker_request.blood_group, available_to_donate=True
    ).all()
    for dp in matching_donors:
        db.session.add(Match(request_id=seeker_request.id, donor_id=dp.user_id))
    db.session.commit()


def process_accept_match(donor_user_id, target_id):
    # 1. Check if target_id corresponds to an existing Match record owned by this donor
    match = Match.query.filter_by(id=target_id, donor_id=donor_user_id).first()

    # 2. If no Match row exists by match_id, check if target_id refers to an open SeekerRequest
    if not match:
        sr = db.session.get(SeekerRequest, target_id)
        if not sr or sr.status != 'open':
            return {'error': 'Request or match not found'}, 404

        dp = DonorProfile.query.filter_by(user_id=donor_user_id).first()
        if not dp or dp.blood_group != sr.blood_group:
            return {'error': 'Blood group mismatch'}, 400

        match = Match.query.filter_by(request_id=sr.id, donor_id=donor_user_id).first()
        if not match:
            match = Match(request_id=sr.id, donor_id=donor_user_id)
            db.session.add(match)

    match.status         = 'accepted'
    match.contact_shared = True
    db.session.commit()

    sr = match.request
    return {
        'success':      True,
        'match_id':     match.id,
        'seeker_name':  sr.user.full_name,
        'seeker_phone': sr.user.phone,
        'hospital':     sr.hospital_name
    }, 200
