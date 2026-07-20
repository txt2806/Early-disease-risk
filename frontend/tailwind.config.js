/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        medical: {
          dark: '#0f172a', // Navy / Slate 900
          trust: '#1e40af', // Blue 800 (Primary Blue)
          accent: '#2563eb', // Blue 600
          light: '#f8fafc', // Slate 50
          gray: '#f1f5f9', // Slate 100
          coral: '#f43f5e', // Rose 500 (Cardio color / Call to action)
          coralhover: '#e11d48' // Rose 600
        }
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
