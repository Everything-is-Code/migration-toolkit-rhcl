import React from 'react';
import { Card, CardBody, Title } from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';

interface Props {
  namespace: string;
}

const ManualSteps: React.FC<Props> = ({ namespace }) => {
  const { t } = useTranslation();
  return (
    <Card><CardBody>
      <Title headingLevel="h3" size="md" style={{ marginBottom: 12 }}>{t('import.manualStepsTitle')}</Title>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 14 }}>
        <div><strong>{t('import.step1Term')}</strong> — {t('import.step1Desc')}</div>
        <div><strong>{t('import.step2Term')}</strong> — <span dangerouslySetInnerHTML={{ __html: t('import.step2Desc') }} /></div>
        <div><strong>{t('import.step3Term')}</strong> — <code>oc apply -n {namespace} -f ./</code></div>
      </div>
    </CardBody></Card>
  );
};

export default ManualSteps;
