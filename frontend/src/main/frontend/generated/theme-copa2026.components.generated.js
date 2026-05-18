import { unsafeCSS, registerStyles } from '@vaadin/vaadin-themable-mixin/register-styles';

import vaadinGridCss from 'themes/copa2026/components/vaadin-grid.css?inline';


if (!document['_vaadintheme_copa2026_componentCss']) {
  registerStyles(
        'vaadin-grid',
        unsafeCSS(vaadinGridCss.toString())
      );
      
  document['_vaadintheme_copa2026_componentCss'] = true;
}

if (import.meta.hot) {
  import.meta.hot.accept((module) => {
    window.location.reload();
  });
}

