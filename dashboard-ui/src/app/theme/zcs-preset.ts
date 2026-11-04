import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

export const ZcsPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#E1F5EE', 100: '#B8ECDE', 200: '#8EE2CC', 300: '#5EEAD4',
      400: '#2DD4BF', 500: '#14B8A6', 600: '#0F9E8F', 700: '#0B6E56',
      800: '#085041', 900: '#04342C', 950: '#02201B'
    },
    colorScheme: {
      light: {
        surface: {
          0: '#FFFFFF', 50: '#F6F7F9', 100: '#F0F2F5', 200: '#E1E4EA',
          300: '#C9CED8', 400: '#AEB4C0', 500: '#8B95A3', 600: '#5A6472',
          700: '#3D4451', 800: '#262B34', 900: '#1A1D23', 950: '#0F1115'
        },
        primary: {
          color: '#0D9488',
          contrastColor: '#FFFFFF',
          hoverColor: '#0B7D72',
          activeColor: '#0A6B62'
        },
        highlight: {
          background: 'rgba(13,148,136,0.08)',
          focusBackground: 'rgba(13,148,136,0.14)',
          color: '#0D9488',
          focusColor: '#0D9488'
        },
        formField: {
          background: '#FFFFFF',
          disabledBackground: '#F0F2F5',
          filledBackground: '#F6F7F9',
          borderColor: '#E1E4EA',
          hoverBorderColor: '#C9CED8',
          focusBorderColor: '#0D9488',
          color: '#1A1D23',
          placeholderColor: '#8B95A3'
        },
        content: {
          background: '#FFFFFF',
          hoverBackground: '#F6F7F9',
          borderColor: '#E1E4EA'
        },
        overlay: {
          select: { background: '#FFFFFF', borderColor: '#E1E4EA' },
          popover: { background: '#FFFFFF', borderColor: '#E1E4EA' },
          modal: { background: '#FFFFFF', borderColor: '#E1E4EA' }
        },
        text: {
          color: '#1A1D23',
          hoverColor: '#0F1115',
          mutedColor: '#5A6472',
          hoverMutedColor: '#3D4451'
        }
      },
      dark: {
        surface: {
          0: '#FFFFFF', 50: '#F1F3F5', 100: '#E5E7EB', 200: '#9CA3AF',
          300: '#7C8593', 400: '#5A6472', 500: '#4B5563', 600: '#374151',
          700: '#262B34', 800: '#1C2028', 900: '#12151C', 950: '#0B0D12'
        },
        primary: {
          color: '#5EEAD4',
          contrastColor: '#04342C',
          hoverColor: '#8EE2CC',
          activeColor: '#2DD4BF'
        },
        highlight: {
          background: 'rgba(94,234,212,0.09)',
          focusBackground: 'rgba(94,234,212,0.14)',
          color: '#5EEAD4',
          focusColor: '#5EEAD4'
        },
        formField: {
          background: '#0E1116',
          disabledBackground: '#12151C',
          filledBackground: '#0E1116',
          borderColor: '#262B34',
          hoverBorderColor: '#374151',
          focusBorderColor: '#5EEAD4',
          color: '#E5E7EB',
          placeholderColor: '#5A6472'
        },
        content: {
          background: '#12151C',
          hoverBackground: '#1C2028',
          borderColor: '#1C2028'
        },
        overlay: {
          select: { background: '#12151C', borderColor: '#262B34' },
          popover: { background: '#12151C', borderColor: '#262B34' },
          modal: { background: '#12151C', borderColor: '#262B34' }
        },
        text: {
          color: '#E5E7EB',
          hoverColor: '#F1F3F5',
          mutedColor: '#7C8593',
          hoverMutedColor: '#9CA3AF'
        }
      }
    }
  },
  components: {
    button: {
      colorScheme: {
        light: {
          root: {
            primary: {
              background: '#0D9488',
              hoverBackground: '#0B7D72',
              activeBackground: '#0A6B62',
              color: '#FFFFFF',
              hoverColor: '#FFFFFF',
              activeColor: '#FFFFFF',
              borderColor: '#0D9488'
            }
          }
        },
        dark: {
          root: {
            primary: {
              background: '#5EEAD4',
              hoverBackground: '#8EE2CC',
              activeBackground: '#2DD4BF',
              color: '#04342C',
              hoverColor: '#04342C',
              activeColor: '#04342C',
              borderColor: '#5EEAD4'
            }
          }
        }
      }
    },
    datatable: {
      colorScheme: {
        light: {
          headerCell: { background: '#F6F7F9', color: '#5A6472' },
          row: { background: '#FFFFFF', hoverBackground: '#F6F7F9' },
          bodyCell: { borderColor: '#E1E4EA' }
        },
        dark: {
          headerCell: { background: '#0E1116', color: '#5A6472' },
          row: { background: '#12151C', hoverBackground: '#161A22' },
          bodyCell: { borderColor: '#1C2028' }
        }
      }
    }
  }
});
