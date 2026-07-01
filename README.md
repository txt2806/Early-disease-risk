# 🩺 CardioCare — Doctor & Patient Portal

CardioCare is an integrated clinic portal system designed to assist Doctors, Receptionists, and Patients. It supports appointment booking, monitoring of cardiovascular clinical metrics, AI-powered health risk prediction, and automated billing/payments integrated with the SePay gateway (VietQR).

---

## 🌐 Live Deployment

The system is deployed and fully accessible online at:
👉 **[https://early-disease-risk.onrender.com](https://early-disease-risk.onrender.com)**

*(Note: The Render free tier may experience a cold start of 1-2 minutes upon the first request. The database is hosted on Supabase, which keeps data synchronized automatically).*

---

## 🚀 Key Features

1. **Patients (Patient Portal):**
   * Book appointments online with specialist doctors.
   * View medical records history and AI-powered cardiovascular risk prediction reports.
   * Submit daily health self-declarations (heart rate, symptoms).
   * Pay bills online using dynamic VietQR codes (automatically updates payment status once paid in full).

2. **Receptionists (Reception Portal):**
   * Register and check in patients at the front desk.
   * Schedule appointments and assign consultation rooms for doctors.
   * Manage clinic invoices: collect cash payments directly or display dynamic QR codes for instant bank transfer verification.

3. **Doctors (Doctor Portal):**
   * Manage assigned patient profiles.
   * Record consultation notes and diagnose diseases using ICD-10 catalogs.
   * Request clinical lab tests and run cardiovascular risk metrics analysis.
   * Custom-tailor abnormal heart rate alert thresholds for specific patients.

4. **Administrators (Admin Portal):**
   * Manage staff profiles (Doctors, Receptionists, Medical Staff) including locking/unlocking accounts.
   * Track system-wide activities using visual Audit Logs.
   * Dynamically adjust clinic fees (General and Specialist consult fees) directly from the management interface.

---

## 🛠️ Technology Stack

* **Backend:** Java 17, Spring Boot 3.2.0, Spring Data JPA, Spring Security (Google OAuth2 & Form Login).
* **Frontend:** Thymeleaf template engine, Vanilla CSS (premium dark mode interface), Tabler Icons.
* **Database:** PostgreSQL (Supabase).
* **Payment Gateway:** SePay Webhook / IPN API (VietQR).

---

## 💾 Database Schema & Initialization

To initialize the project's database, run the following SQL script in your database management tool (e.g. Supabase SQL Editor):

```sql
-- Run the script below to initialize the database tables
CREATE TABLE IF NOT EXISTS public.patient_profile (
  patientid SERIAL PRIMARY KEY,
  address VARCHAR(255),
  dob DATE NOT NULL,
  fullname VARCHAR(255) NOT NULL,
  gender VARCHAR(50) NOT NULL,
  passwordhash VARCHAR(255) NOT NULL,
  phone VARCHAR(50),
  status VARCHAR(50) DEFAULT 'ACTIVE',
  username VARCHAR(255) NOT NULL UNIQUE,
  is_alert INTEGER DEFAULT 0,
  created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.doctor_profile (
  doctorid SERIAL PRIMARY KEY,
  alertthreshold_bp VARCHAR(50) DEFAULT '140/90',
  alertthreshold_bpm INTEGER DEFAULT 100,
  fullname VARCHAR(255) NOT NULL,
  licensenumber VARCHAR(100),
  passwordhash VARCHAR(255) NOT NULL,
  specialty VARCHAR(255) DEFAULT 'Tim mạch',
  status VARCHAR(50) DEFAULT 'ACTIVE',
  username VARCHAR(255) NOT NULL UNIQUE,
  roomnumber VARCHAR(100) DEFAULT 'Chưa xếp phòng'
);

CREATE TABLE IF NOT EXISTS public.staff_profile (
  staffid SERIAL PRIMARY KEY,
  fullname VARCHAR(255) NOT NULL,
  passwordhash VARCHAR(255) NOT NULL,
  role VARCHAR(100) NOT NULL, -- RECEPTIONIST, STAFF
  status VARCHAR(50) DEFAULT 'ACTIVE',
  username VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS public.appointment (
  appointmentid SERIAL PRIMARY KEY,
  patientid INTEGER REFERENCES public.patient_profile(patientid) ON DELETE CASCADE,
  doctorid INTEGER REFERENCES public.doctor_profile(doctorid) ON DELETE SET NULL,
  scheduleddate DATE NOT NULL,
  timeslot TIME WITHOUT TIME ZONE,
  endtime TIME WITHOUT TIME ZONE,
  status VARCHAR(50) DEFAULT 'Pending', -- Pending, Confirmed, CheckedIn, InProgress, Completed, Cancelled
  preliminarystatus VARCHAR(255),
  roomnumber VARCHAR(100),
  requesttime TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  queuenumber INTEGER,
  bookingtype VARCHAR(100) DEFAULT 'General'
);

CREATE TABLE IF NOT EXISTS public.invoice (
  invoiceid SERIAL PRIMARY KEY,
  appointmentid INTEGER UNIQUE REFERENCES public.appointment(appointmentid) ON DELETE CASCADE,
  patientid INTEGER REFERENCES public.patient_profile(patientid) ON DELETE CASCADE,
  amount BIGINT NOT NULL,
  paidamount BIGINT NOT NULL DEFAULT 0,
  paymentmethod VARCHAR(100),
  status VARCHAR(50) NOT NULL DEFAULT 'Unpaid', -- Unpaid, PartiallyPaid, Paid
  referencecode VARCHAR(100),
  createddate TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  paymentdate TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE IF NOT EXISTS public.system_setting (
  settingkey VARCHAR(100) PRIMARY KEY,
  settingvalue VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS public.system_log (
  logid SERIAL PRIMARY KEY,
  action VARCHAR(255) NOT NULL,
  details TEXT,
  timestamp TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  username VARCHAR(255) NOT NULL
);

-- Seed default clinic fees
INSERT INTO public.system_setting (settingkey, settingvalue) VALUES ('fee_general', '150000') ON CONFLICT (settingkey) DO NOTHING;
INSERT INTO public.system_setting (settingkey, settingvalue) VALUES ('fee_specialist', '300000') ON CONFLICT (settingkey) DO NOTHING;
```

---

## ⚙️ Application Configuration (`application.properties`)

Edit database connection credentials and SePay credentials in `src/main/resources/application.properties`:

```properties
# SUPABASE CONNECTION
spring.datasource.url=jdbc:postgresql://<host>:<port>/postgres?sslmode=require
spring.datasource.username=<username>
spring.datasource.password=<password>

# SEPAY INTEGRATION
sepay.api.key=OEYETO1H1DYIZMQWCOCINZRB5VXJQQ3KJDR73INFYMFB6VHGTX0MSATIFSOTBUWU
sepay.bank.id=MB
sepay.bank.account=966662869999
sepay.bank.owner=TRAN XUAN THANH
```

---

## 🏃 Local Setup & Running

1. Verify that **Java JDK 17** is installed and the `JAVA_HOME` environment variable is configured.
2. Open the project root directory in a terminal and compile classes:
   ```bash
   # Windows Command Prompt / PowerShell
   .\mvnw.cmd clean compile
   ```
3. Run the Spring Boot application:
   ```bash
   .\mvnw.cmd spring-boot:run
   ```
4. Access the web app in your browser: `http://localhost:8080`

---

## 🔌 SePay Webhook / IPN Integration

* The portal exposes two anonymous API endpoints bypassing CSRF protection for transaction callbacks:
  * **Balance changes webhook:** `POST /api/sepay/webhook`
  * **Order Payment gateway callback (IPN):** `POST /api/sepay/ipn`
* Set your SePay Webhook / IPN callback URL to: `https://early-disease-risk.onrender.com/api/sepay/webhook` (or `/api/sepay/ipn`) with the `Authorization` header containing the matching API Key configured in your application properties.
* Required Bank transfer description/memo format: **`TT` + `{invoiceId}`** (e.g., `TT1024`).

---

## 🛠️ Testing Webhooks & Local Development

Since local servers (`http://localhost:8080`) are not publicly accessible by SePay servers, you can test transaction callbacks using one of these methods:

### Method 1: Mocking Callbacks via Postman (Recommended for fast dev tests)
You can directly mock the transaction payload by triggering your local server endpoint using an API client like Postman:
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/sepay/webhook`
* **Headers:** 
  * `Content-Type: application/json`
  * `Authorization: Apikey OEYETO1H1DYIZMQWCOCINZRB5VXJQQ3KJDR73INFYMFB6VHGTX0MSATIFSOTBUWU`
* **JSON Body Example (Mocking paid in full):**
  ```json
  {
    "id": 9991234,
    "gateway": "MBBank",
    "transactionDate": "2026-07-01 12:00:00",
    "accountNumber": "966662869999",
    "code": "TT1001",
    "content": "Chuyen khoan TT1001",
    "transferType": "in",
    "description": "TEST PAYMENT",
    "transferAmount": 150000,
    "accumulated": 150000,
    "referenceCode": "FT123456"
  }
  ```
  *(Replace `TT1001` with the active invoice's reference code, and `150000` with the invoice amount. The server will update the status and auto-confirm the appointment).*

### Method 2: Real Payments Synced through Render & Shared Supabase DB
Because both your local app and the live Render site connect to the **same Supabase PostgreSQL database**, any real bank transfer webhook sent to `https://early-disease-risk.onrender.com/api/sepay/webhook` will update the database. Since your local app reads from the exact same database, the payment status will immediately show as "Paid" on your local UI!

### Method 3: Tunneling local server using Ngrok
If you want SePay to trigger your local app instance directly:
1. Start ngrok tunnel pointing to port 8080:
   ```bash
   ngrok http 8080
   ```
2. Copy the generated public URL (e.g. `https://xxxx.ngrok-free.app`).
3. Set your callback URL in SePay Dashboard to: `https://xxxx.ngrok-free.app/api/sepay/webhook`.

