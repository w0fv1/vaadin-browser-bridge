import { UserConfigFn } from 'vite';
import { overrideVaadinConfig } from './vite.generated.ts';

const customConfig: UserConfigFn = () => ({
  plugins: [
    {
      name: 'browser-bridge-disable-broken-typescript-checker',
      configResolved(config) {
        config.plugins = config.plugins.filter((plugin) => plugin.name !== 'vite-plugin-checker');
      },
    },
  ],
});

export default overrideVaadinConfig(customConfig);
