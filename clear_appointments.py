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
    
    cursor.execute("TRUNCATE TABLE public.invoice, public.appointment RESTART IDENTITY CASCADE;")
    print("Successfully deleted all appointments and invoices from database.")
    
    cursor.close()
    conn.close()
except Exception as e:
    print(f"Error: {e}")
