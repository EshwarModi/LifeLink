from flask import Blueprint, request, jsonify, session
from services import request_service

requests_bp = Blueprint('requests', __name__)


@requests_bp.route('/seeker/create-request', methods=['POST'], endpoint='create_request')
def create_request():
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return jsonify({'error': 'Unauthorized'}), 401

    data = request.get_json()
    res, status = request_service.create_seeker_request(session['user_id'], data)
    return jsonify(res), status


@requests_bp.route('/seeker/update-request/<int:request_id>', methods=['POST'], endpoint='update_request_status')
def update_request_status(request_id):
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return jsonify({'error': 'Unauthorized'}), 401

    data = request.get_json()
    res, status = request_service.update_request_status(session['user_id'], request_id, data)
    return jsonify(res), status


@requests_bp.route('/seeker/edit-request/<int:request_id>', methods=['POST'], endpoint='edit_request')
def edit_request(request_id):
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return jsonify({'error': 'Unauthorized'}), 401

    data = request.get_json()
    res, status = request_service.edit_seeker_request(session['user_id'], request_id, data)
    return jsonify(res), status
