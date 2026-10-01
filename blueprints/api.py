from flask import Blueprint, request, jsonify, session
from extensions import db
from services import api_service, matching_service

api_bp = Blueprint('api', __name__)


@api_bp.route('/api/find-requests', methods=['GET'], endpoint='find_requests')
def find_requests():
    if 'user_id' not in session or session['user_type'] != 'donor':
        return jsonify({'error': 'Unauthorized'}), 401

    res, status = api_service.get_requests_for_donor(session['user_id'])
    return jsonify(res), status


@api_bp.route('/api/accept-match/<int:target_id>', methods=['POST'], endpoint='accept_match')
def accept_match(target_id):
    if 'user_id' not in session or session['user_type'] != 'donor':
        return jsonify({'error': 'Unauthorized'}), 401

    res, status = matching_service.process_accept_match(session['user_id'], target_id)
    return jsonify(res), status


@api_bp.route('/api/donor/update-availability', methods=['POST'], endpoint='update_donor_availability')
def update_donor_availability():
    if 'user_id' not in session or session['user_type'] != 'donor':
        return jsonify({'error': 'Unauthorized'}), 401

    data = request.get_json()
    res, status = api_service.update_donor_availability(session['user_id'], data)
    return jsonify(res), status


@api_bp.route('/api/find-donors', methods=['GET'], endpoint='find_donors')
def find_donors():
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401

    blood_group = request.args.get('blood_group')
    city        = request.args.get('city')
    res, status = api_service.find_donors(blood_group, city)
    return jsonify(res), status


@api_bp.route('/health', endpoint='health')
def health():
    try:
        db.session.execute(db.text('SELECT 1'))
        db_status = 'ok'
    except Exception:
        db_status = 'error'
    return jsonify({'status': 'ok', 'db': db_status}), 200
