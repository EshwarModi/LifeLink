# LifeLink - Blood Donation Platform

LifeLink is a comprehensive web platform that connects blood donors with people in urgent need of blood transfusions. The platform facilitates safe, efficient, and organized blood donation management.

## Features

### For Donors
- 👤 Create and manage donor profile
- 🔍 Browse active blood requests in real-time
- 📍 Filter requests by location and urgency
- 📱 Direct contact sharing with seekers
- 📊 Track donation history
- 🔔 Get matched with compatible requests automatically

### For Seekers
- 🆘 Create urgent or normal blood requests
- 🏥 Specify hospital details and requirements
- 📦 Track multiple units needed
- 🎯 Get matched with available donors automatically
- 📞 Contact matched donors securely
- ✅ Mark requests as fulfilled when complete

### General Features
- 🔐 Secure user authentication & authorization
- 🗺️ Location-based matching system
- 🩸 Support for all 8 blood groups (A+, A-, B+, B-, O+, O-, AB+, AB-)
- 📱 Responsive design for mobile & desktop
- 💾 SQLite database for easy setup
- 🔒 Privacy-focused contact sharing

## Tech Stack

- **Backend**: Flask (Python)
- **Database**: SQLite
- **Frontend**: HTML5, CSS3, JavaScript (ES6+)
- **Template Engine**: Jinja2

## Project Structure

```
gitproject/
├── app.py                  # Main Flask application
├── requirements.txt        # Python dependencies
├── lifelink.db            # SQLite database (created on first run)
└── templates/
    ├── base.html          # Base template with header/footer
    ├── home.html          # Home page
    ├── register.html      # User registration
    ├── login.html         # User login
    ├── donor.html         # Donor dashboard
    └── seeker.html        # Seeker dashboard
```

## Installation

### Prerequisites
- Python 3.7 or higher
- pip (Python package manager)

### Setup Steps

1. **Clone the repository** (if not already done)
   ```bash
   git clone https://github.com/EshwarModi/LifeLink.git
   cd gitproject
   ```

2. **Create a virtual environment**
   ```bash
   # On Windows
   python -m venv venv
   venv\Scripts\activate
   
   # On macOS/Linux
   python3 -m venv venv
   source venv/bin/activate
   ```

3. **Install dependencies**
   ```bash
   pip install -r requirements.txt
   ```

4. **Set environment variables** (optional)
   ```bash
   # On Windows (PowerShell)
   $env:FLASK_APP = "app.py"
   $env:FLASK_ENV = "development"
   
   # On macOS/Linux
   export FLASK_APP=app.py
   export FLASK_ENV=development
   ```

5. **Run the application**
   ```bash
   python app.py
   ```

6. **Access the application**
   - Open your browser and go to `http://localhost:5000`
   - The database will be created automatically on first run

## Usage

### For Donors

1. **Register**: Click "Register" and select "I want to donate blood"
2. **Complete Profile**: Enter your blood group, age, gender, and location
3. **Browse Requests**: Visit dashboard to see blood requests
4. **Respond to Requests**: Click "I Can Help" on urgent or normal requests
5. **Share Contact**: Confirm matching and share your contact information

### For Seekers

1. **Register**: Click "Register" and select "I need blood"
2. **Create Request**: Click "Create Request" button on dashboard
3. **Specify Requirements**:
   - Blood group needed
   - Number of units
   - Urgency level
   - Hospital details
   - Required date/time
4. **Get Matched**: System automatically matches you with eligible donors
5. **Coordinate**: Contact matched donors to arrange transfusion

## Database Models

### User
- Stores authentication and personal information
- Types: 'donor' or 'seeker'

### DonorProfile
- Blood group, age, gender
- Last donation date
- Medical conditions
- Availability status

### SeekerRequest
- Blood requirements
- Hospital information
- Urgency level
- Status tracking

### Match
- Connects donors with requests
- Contact sharing status
- Match timestamp

## API Endpoints

### Authentication
- `POST /register` - Register new user
- `POST /login` - Login user
- `GET /logout` - Logout user

### Dashboard
- `GET /dashboard` - User dashboard (redirects to type-specific dashboard)
- `GET /donor/dashboard` - Donor dashboard
- `GET /seeker/dashboard` - Seeker dashboard

### Data Operations
- `POST /seeker/create-request` - Create blood request
- `GET /api/find-donors` - Find available donors (for seekers)
- `GET /api/find-requests` - Find blood requests (for donors)
- `POST /api/accept-match/<id>` - Accept match and share contact

## Security Considerations

⚠️ **Important for Production**:

1. **Change Secret Key**: Update `app.config['SECRET_KEY']` in app.py
2. **Use Environment Variables**: Store sensitive configuration in .env file
3. **Enable HTTPS**: Use SSL/TLS in production
4. **Database Security**: Use a more robust database (PostgreSQL)
5. **Input Validation**: All inputs are validated
6. **Password Hashing**: Passwords are hashed using werkzeug

### Example .env Setup
```
FLASK_SECRET_KEY=your-very-secret-key-here
FLASK_ENV=production
DATABASE_URL=postgresql://user:password@localhost/lifelink
```

## Future Enhancements

- [ ] Admin dashboard for platform management
- [ ] Email notifications for matches
- [ ] SMS notifications for urgent requests
- [ ] Payment integration for blood bank partnerships
- [ ] Hospital verification system
- [ ] Donor screening questionnaire
- [ ] Blood drive event management
- [ ] Mobile app (iOS/Android)
- [ ] Advanced reporting & analytics
- [ ] Integration with blood banks
- [ ] AI-based matching algorithm
- [ ] Donor eligibility auto-check

## Troubleshooting

### Port already in use
```bash
# Change the port in app.py
app.run(debug=True, port=5001)
```

### Database issues
```bash
# Delete existing database and recreate
rm lifelink.db  # On Windows: del lifelink.db
python app.py  # Run again to create fresh database
```

### Module not found errors
```bash
# Ensure virtual environment is activated
# Reinstall dependencies
pip install --upgrade -r requirements.txt
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is open source and available under the MIT License.

## Contact & Support

For questions, suggestions, or issues:
- Create an issue on GitHub
- Contact: contact@lifelink.com
- Website: www.lifelink.com

---

**Made with ❤️ to save lives through blood donation**

Last Updated: May 2024
