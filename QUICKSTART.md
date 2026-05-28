# LifeLink - Quick Start Guide

## 🚀 Getting Started (Windows)

### 1. Open PowerShell in the project folder

```powershell
cd C:\Users\hp\Desktop\gitproject
```

### 2. Create Virtual Environment

```powershell
python -m venv venv
```

### 3. Activate Virtual Environment

```powershell
venv\Scripts\Activate.ps1
```

If you get an error about execution policies, run:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### 4. Install Dependencies

```powershell
pip install -r requirements.txt
```

### 5. Run the Application

```powershell
python app.py
```

### 6. Open in Browser

Navigate to: `http://localhost:5000`

---

## 📝 Test Accounts

### Donor Test Account
- **Username**: donor1
- **Password**: password123
- **Blood Group**: O+

### Seeker Test Account  
- **Username**: seeker1
- **Password**: password123

---

## 🧪 Testing the Platform

### Step 1: Register as a Donor
1. Click "Register"
2. Select "I want to donate blood"
3. Fill in details with Blood Group: O+
4. Create account

### Step 2: Register as a Seeker
1. Open in incognito/private browser
2. Click "Register"
3. Select "I need blood"
4. Fill in details
5. Create account

### Step 3: Create Blood Request (as Seeker)
1. Log in as seeker
2. Click "Create Request"
3. Fill details (O+ blood, 2 units, urgent)
4. Submit

### Step 4: View Matches (as Donor)
1. Log in as donor
2. View urgent requests
3. Click "I Can Help"
4. Respond to the seeker

---

## 🔧 Development Tips

### Debug Mode
- Already enabled in app.py
- Auto-reloads on file changes
- Accessible at http://localhost:5000

### Database
- Located at: `lifelink.db`
- Auto-created on first run
- To reset: Delete the file and restart

### Static Files
- CSS is inline in templates
- JavaScript is inline in templates
- Add external styles in templates/base.html

### Common Issues

**Port 5000 already in use?**
```powershell
# Change port in app.py
# app.run(debug=True, port=5001)
```

**Module not found?**
```powershell
# Make sure virtual env is activated
# Reinstall: pip install -r requirements.txt
```

**Database locked?**
```powershell
# Close all instances and restart
# Or delete lifelink.db and restart
```

---

## 📁 File Organization

```
templates/
  ├── base.html          ← Shared layout
  ├── home.html          ← Landing page
  ├── register.html      ← Registration form
  ├── login.html         ← Login page
  ├── donor.html         ← Donor dashboard
  └── seeker.html        ← Seeker dashboard

app.py                    ← Main application
requirements.txt          ← Dependencies
lifelink.db              ← Database (auto-created)
```

---

## 🚀 Next Steps

1. ✅ Run the application
2. ✅ Test donor registration
3. ✅ Test seeker registration  
4. ✅ Create a blood request
5. ✅ View matching donors
6. ✅ Share contact information
7. 🔄 Customize styling in templates/base.html
8. 🔄 Add more features!

---

## 📞 Need Help?

Check the main README.md for:
- Complete feature list
- Database schema
- API documentation
- Deployment instructions
