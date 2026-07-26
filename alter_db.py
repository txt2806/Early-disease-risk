import psycopg2

try:
    conn = psycopg2.connect(
        dbname="postgres",
        user="postgres.xmegdperrxmprgxqvkaq",
        password="5RBONt4L9dojNB1G",
        host="aws-1-ap-southeast-1.pooler.supabase.com",
        port="6543",
        sslmode="require"
    )
    conn.autocommit = True
    cursor = conn.cursor()
    cursor.execute("ALTER TABLE Patient_Self_Monitoring ADD COLUMN IF NOT EXISTS Duration VARCHAR(100);")
    cursor.execute("ALTER TABLE Patient_Self_Monitoring ADD COLUMN IF NOT EXISTS Notes TEXT;")
    cursor.execute("ALTER TABLE Patient_Self_Monitoring ADD COLUMN IF NOT EXISTS SeverityScore INT;")
    print("Successfully added Duration, Notes, and SeverityScore columns.")
    cursor.close()
    conn.close()
except Exception as e:
    print(f"Error: {e}")
