import React from 'react';
import Navbar from './components/Navbar';
import Hero from './components/Hero';
import Stats from './components/Stats';
import Services from './components/Services';
import Doctors from './components/Doctors';
import BookingForm from './components/BookingForm';
import Footer from './components/Footer';

function App() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 font-sans antialiased scroll-smooth">
      {/* Navigation Header */}
      <Navbar />

      {/* Hero section */}
      <Hero />

      {/* Highlight Stats (bridging hero and services) */}
      <Stats />

      {/* Services Grid */}
      <Services />

      {/* Doctors Profiles Roster */}
      <Doctors />

      {/* Booking Scheduling Form */}
      <BookingForm />

      {/* Footer Contact Info */}
      <Footer />
    </div>
  );
}

export default App;
