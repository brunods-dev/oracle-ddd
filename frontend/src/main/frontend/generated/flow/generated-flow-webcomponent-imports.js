import { injectGlobalWebcomponentCss } from 'Frontend/generated/jar-resources/theme-util.js';

import '@vaadin/polymer-legacy-adapter/style-modules.js';
import '@vaadin/vertical-layout/theme/lumo/vaadin-vertical-layout.js';
import '@vaadin/app-layout/theme/lumo/vaadin-app-layout.js';
import '@vaadin/app-layout/theme/lumo/vaadin-drawer-toggle.js';
import '@vaadin/button/theme/lumo/vaadin-button.js';
import '@vaadin/tooltip/theme/lumo/vaadin-tooltip.js';
import 'Frontend/generated/jar-resources/disableOnClickFunctions.js';
import '@vaadin/icons/vaadin-iconset.js';
import '@vaadin/icon/theme/lumo/vaadin-icon.js';
import '@vaadin/horizontal-layout/theme/lumo/vaadin-horizontal-layout.js';
import '@vaadin/scroller/theme/lumo/vaadin-scroller.js';
import '@vaadin/side-nav/theme/lumo/vaadin-side-nav.js';
import '@vaadin/side-nav/theme/lumo/vaadin-side-nav-item.js';
import '@vaadin/notification/theme/lumo/vaadin-notification.js';
import 'Frontend/generated/jar-resources/flow-component-renderer.js';
import '@vaadin/grid/theme/lumo/vaadin-grid.js';
import '@vaadin/grid/theme/lumo/vaadin-grid-column.js';
import '@vaadin/grid/theme/lumo/vaadin-grid-sorter.js';
import '@vaadin/checkbox/theme/lumo/vaadin-checkbox.js';
import 'Frontend/generated/jar-resources/gridConnector.ts';
import 'Frontend/generated/jar-resources/vaadin-grid-flow-selection-column.js';
import '@vaadin/grid/theme/lumo/vaadin-grid-column-group.js';
import 'Frontend/generated/jar-resources/lit-renderer.ts';
import '@vaadin/context-menu/theme/lumo/vaadin-context-menu.js';
import 'Frontend/generated/jar-resources/contextMenuConnector.js';
import 'Frontend/generated/jar-resources/contextMenuTargetConnector.js';
import '@vaadin/text-field/theme/lumo/vaadin-text-field.js';
import '@vaadin/combo-box/theme/lumo/vaadin-combo-box.js';
import 'Frontend/generated/jar-resources/comboBoxConnector.js';
import '@vaadin/multi-select-combo-box/theme/lumo/vaadin-multi-select-combo-box.js';
import '@vaadin/number-field/theme/lumo/vaadin-number-field.js';
import '@vaadin/common-frontend/ConnectionIndicator.js';
import '@vaadin/vaadin-lumo-styles/sizing.js';
import '@vaadin/vaadin-lumo-styles/spacing.js';
import '@vaadin/vaadin-lumo-styles/style.js';
import '@vaadin/vaadin-lumo-styles/vaadin-iconset.js';
import 'Frontend/generated/jar-resources/ReactRouterOutletElement.tsx';

const loadOnDemand = (key) => {
  const pending = [];
  if (key === '9d707c211c196bfd02806d865c3912f9a2026226736bbfa4dabc6eeab1d36d16') {
    pending.push(import('./chunks/chunk-e6d1859667eeb00ffc89e4c6e1db4ac72c7e47157ccbdad1c15981f31ba54de1.js'));
  }
  if (key === '334b9e07d3e742f9bb43c5dce4b74fc8c0f1d00eef93eceffe0825f56770d6f8') {
    pending.push(import('./chunks/chunk-d7daa9648542f740f9341ce042b8dc8e43ad892dc311b1e7ac43fc96d64a2095.js'));
  }
  if (key === '53b3342f4aa006697da4cc5d87b46a9a8ca0d768889a7451826774192bf0aba6') {
    pending.push(import('./chunks/chunk-947c0e4ffd98af71584c75175235bce360d31834560a60425bc9517f8b41f7a9.js'));
  }
  if (key === '64907ca1dd681a49526775dd3cce1af26f2e306109dd1f1b5b0765209f297adf') {
    pending.push(import('./chunks/chunk-00ae07ebf7957476b68925b1e3ff21b6d73a68343e9c266324892e76bb16b9ca.js'));
  }
  if (key === '606e37b07c17223a2c8ea5f1c121f79288f1b146ca3c870d44335e5b9f34fa25') {
    pending.push(import('./chunks/chunk-947c0e4ffd98af71584c75175235bce360d31834560a60425bc9517f8b41f7a9.js'));
  }
  if (key === 'b9c228948ddea74c8d2d5b2f63044441b510684ca4a82b7cc1f75fb3f3a66eec') {
    pending.push(import('./chunks/chunk-947c0e4ffd98af71584c75175235bce360d31834560a60425bc9517f8b41f7a9.js'));
  }
  if (key === 'c0a45696e56c3496ed5472cf5e7b1c29117ea120c602c927fb382ac56adbd588') {
    pending.push(import('./chunks/chunk-27d5d2a6bcc062c591a7179556a17dbd8e10ba62123a77497321f1361679185f.js'));
  }
  if (key === '8dd999039c7c09cfc794bb203854dfc2118d08a3139ccd9c4792e0ad039a4157') {
    pending.push(import('./chunks/chunk-1d69f5a5fea05f31b628dafb6d3b878584eb1f5d663feada4b9ff0f874ff8eb1.js'));
  }
  if (key === '6eb529d005a40acf5b877b1b07291b6c367ea53c0991bc0b54a660ba4c35e013') {
    pending.push(import('./chunks/chunk-947c0e4ffd98af71584c75175235bce360d31834560a60425bc9517f8b41f7a9.js'));
  }
  return Promise.all(pending);
}

window.Vaadin = window.Vaadin || {};
window.Vaadin.Flow = window.Vaadin.Flow || {};
window.Vaadin.Flow.loadOnDemand = loadOnDemand;
window.Vaadin.Flow.resetFocus = () => {
 let ae=document.activeElement;
 while(ae&&ae.shadowRoot) ae = ae.shadowRoot.activeElement;
 return !ae || ae.blur() || ae.focus() || true;
}