/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/**/*.{html,ts}'
  ],
  theme: {
    extend: {
      colors: {
        dt: {
          50: '#FEF2F7',
          100: '#FDE8F0',
          200: '#FCC7E1',
          300: '#FAA6D2',
          400: '#F67EC0',
          500: '#E20074',
          600: '#C6006A',
          700: '#A20060',
          800: '#7E0050',
          900: '#5A0040'
        },
        magenta: {
          50: '#FEF2F7',
          100: '#FDE8F0',
          200: '#FCC7E1',
          300: '#FAA6D2',
          400: '#F67EC0',
          500: '#E20074',
          600: '#C6006A',
          700: '#A20060',
          800: '#7E0050',
          900: '#5A0040'
        }
      },
      boxShadow: {
        panel: '0 10px 30px rgba(112, 26, 117, 0.12)',
        'nav': '0 4px 12px rgba(226, 0, 116, 0.2)'
      },
      fontFamily: {
        sans: ['Inter', 'Segoe UI', 'system-ui', 'sans-serif']
      }
    }
  },
  plugins: []
};

