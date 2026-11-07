import type { ReactNode } from 'react';

interface PageHeadingProps {
  eyebrow: string;
  title: string;
  description: ReactNode;
  extra?: ReactNode;
}

export default function PageHeading({ eyebrow, title, description, extra }: PageHeadingProps) {
  return (
    <div className="page-heading">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        <div className="page-description">{description}</div>
      </div>
      {extra ? <div className="page-heading-extra">{extra}</div> : null}
    </div>
  );
}
