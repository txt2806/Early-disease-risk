import React, { useState, useEffect } from 'react';
import { Menu, X, HeartPulse } from 'lucide-react';

export default function Navbar() {
  const [isOpen, setIsOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      if (window.scrollY > 20) {
        setScrolled(true);
      } else {
        setScrolled(false);
      }
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const menuItems = [
    { name: 'Trang chủ', href: '#home' },
    { name: 'Dịch vụ', href: '#services' },
    { name: 'Đội ngũ bác sĩ', href: '#doctors' },
    { name: 'Đặt lịch khám', href: '#booking' }
  ];

  return (
    <nav className={`fixed w-full z-50 transition-all duration-300 ${
      scrolled 
        ? 'bg-medical-dark/95 backdrop-blur-md border-b border-white/10 shadow-lg py-3' 
        : 'bg-transparent py-5'
    }`}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-12">
          
          {/* Logo */}
          <div className="flex-shrink-0 flex items-center gap-2 select-none cursor-pointer" onClick={() => window.scrollTo({top: 0, behavior: 'smooth'})}>
            <HeartPulse className="h-8 w-8 text-medical-coral animate-pulse" />
            <span className="text-white font-extrabold text-xl tracking-tight">
              CARDIO<span className="text-medical-coral">CARE</span>
            </span>
          </div>

          {/* Navigation Links - Desktop */}
          <div className="hidden md:flex items-center gap-8">
            {menuItems.map((item) => (
              <a
                key={item.name}
                href={item.href}
                className="text-slate-300 hover:text-white font-medium text-sm transition-colors duration-200"
              >
                {item.name}
              </a>
            ))}
            
            {/* CTA Button */}
            <a
              href="#booking"
              className="bg-medical-coral hover:bg-medical-coralhover text-white px-6 py-2.5 rounded-full text-sm font-bold shadow-md shadow-medical-coral/20 transition-all duration-200 hover:-translate-y-0.5 active:translate-y-0"
            >
              Đặt lịch ngay
            </a>
          </div>

          {/* Mobile Menu Button */}
          <div className="md:hidden">
            <button
              onClick={() => setIsOpen(!isOpen)}
              className="inline-flex items-center justify-center p-2 rounded-md text-slate-300 hover:text-white focus:outline-none"
            >
              {isOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
            </button>
          </div>

        </div>
      </div>

      {/* Mobile Menu Drawer */}
      <div className={`md:hidden transition-all duration-300 ${isOpen ? 'max-h-screen opacity-100' : 'max-h-0 opacity-0 overflow-hidden'}`}>
        <div className="px-2 pt-2 pb-4 space-y-1 bg-medical-dark/98 border-b border-white/10 shadow-2xl">
          {menuItems.map((item) => (
            <a
              key={item.name}
              href={item.href}
              onClick={() => setIsOpen(false)}
              className="block px-4 py-3 rounded-md text-base font-semibold text-slate-300 hover:text-white hover:bg-white/5 transition-colors"
            >
              {item.name}
            </a>
          ))}
          <div className="px-4 pt-4 border-t border-white/10">
            <a
              href="#booking"
              onClick={() => setIsOpen(false)}
              className="block w-full text-center bg-medical-coral hover:bg-medical-coralhover text-white py-3 rounded-full text-base font-bold transition-all shadow-lg"
            >
              Đặt lịch khám
            </a>
          </div>
        </div>
      </div>
    </nav>
  );
}
