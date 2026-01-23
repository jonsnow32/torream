# UI Theme Customization Guide

This documentation is built using Docusaurus with a beautiful custom UI theme inspired by [kuizuo/blog](https://github.com/kuizuo/blog).

## What's Included

### 1. **TailwindCSS Integration**
- Full TailwindCSS v3 support with Docusaurus
- Custom color system tied to Docusaurus CSS variables
- Dark mode support using CSS custom properties

### 2. **Custom Color Scheme**
The theme uses a carefully designed color palette:

**Light Mode:**
- Primary: `#12affa` (Modern Blue)
- Background: `#fefefe`
- Text: `#333`

**Dark Mode:**
- Primary: Computed dynamically for optimal contrast
- Background: `#18181b` (Deep dark)
- Text: `#eceef1`

### 3. **CSS Variable System**
All colors are defined as CSS custom properties in `src/css/custom.css`:

```css
:root {
  --content-background: #fefefe;
  --ifm-color-primary: #12affa;
  /* ... more variables ... */
}
```

### 4. **Utility Functions**
The `cn()` utility function (in `src/lib/utils.ts`) helps merge TailwindCSS classes cleanly:

```typescript
import { cn } from '@site/src/lib/utils'

export function MyComponent() {
  return <div className={cn('px-4', 'py-2', 'bg-primary')} />
}
```

## Key Files

- **tailwind.config.ts** - TailwindCSS configuration
- **src/css/custom.css** - Global styles and theme variables
- **src/lib/utils.ts** - Utility function for class merging
- **docusaurus.config.js** - Docusaurus configuration with Tailwind plugin
- **src/theme/MyLayout/index.tsx** - Custom layout component example

## Customizing Colors

To change the theme colors, edit the CSS variables in `src/css/custom.css`:

```css
:root {
  --ifm-color-primary: #your-color-here;
  --ifm-color-primary-dark: #darker-shade;
  /* ... */
}
```

The changes will automatically apply to:
- All Docusaurus components
- Custom components using TailwindCSS
- Code block highlighting
- Links and interactive elements

## Using TailwindCSS Classes

You can now use TailwindCSS utilities directly in your components:

```jsx
<div className="flex items-center gap-4 px-6 py-3 bg-blue-500 rounded-lg shadow-lg">
  <span className="text-white font-semibold">Hello World</span>
</div>
```

## Dark Mode

Dark mode is automatically handled via Docusaurus' color mode toggle. The theme respects the user's preference using the `[data-theme="dark"]` selector.

Custom components should use:

```jsx
<div className="bg-white dark:bg-slate-900 text-black dark:text-white">
  Content
</div>
```

## Extending the Theme

### Adding Custom Components
Create custom theme components in `src/theme/` directory:

```typescript
// src/theme/MyButton/index.tsx
import { cn } from '@site/src/lib/utils'

export default function MyButton({ children, variant = 'primary' }) {
  return (
    <button
      className={cn(
        'px-4 py-2 rounded-lg font-semibold transition-colors',
        variant === 'primary' && 'bg-primary text-white hover:bg-primary-dark',
        variant === 'secondary' && 'bg-gray-200 text-gray-900 hover:bg-gray-300'
      )}
    >
      {children}
    </button>
  )
}
```

### Adding Custom CSS
Add custom styles to `src/css/custom.css`:

```css
@layer components {
  .custom-card {
    @apply p-4 rounded-lg shadow-md bg-white dark:bg-slate-800;
  }
}
```

## Browser Support

This theme supports all modern browsers:
- Chrome/Edge (latest)
- Firefox (latest)
- Safari (latest)
- Mobile browsers

## Resources

- [Tailwind CSS Documentation](https://tailwindcss.com)
- [Docusaurus Styling Guide](https://docusaurus.io/docs/styling-layout)
- [Original Theme Repository](https://github.com/kuizuo/blog)

## Performance

The theme is optimized for performance:
- CSS-in-JS is avoided, using CSS custom properties instead
- TailwindCSS is configured with `preflight: false` to avoid conflicts with Docusaurus
- Styles are automatically purged for unused classes

---

Enjoy your beautifully themed documentation! 🎨
