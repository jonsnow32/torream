import { cn } from '@site/src/lib/utils'
import type { Props } from '@theme/Layout'
import Layout from '@theme/Layout'

export default function MyLayout({ children, maxWidth = 1024, ...layoutProps }: Props & { maxWidth?: number }): JSX.Element {
  return (
    <Layout {...layoutProps}>
      <div className="bg-background dark:bg-background">
        <div
          className={cn('mx-auto px-4', 'margin-vert--lg')}
          style={maxWidth ? { maxWidth: `${maxWidth}px` } : {}}
        >
          <main>{children}</main>
        </div>
      </div>
    </Layout>
  )
}
