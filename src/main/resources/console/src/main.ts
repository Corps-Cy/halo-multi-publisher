import { definePlugin } from "@halo-dev/console-shared";
import PlatformList from "./index.ts";
import PlatformForm from "./PlatformForm.vue";

export default definePlugin({
  components: {},
  routes: [
    {
      path: "/sync-platforms",
      component: PlatformList,
      meta: {
        title: "多平台同步",
        menu: {
          name: "多平台同步",
          group: "content",
          icon: "ri:share-circle-line",
          priority: 50,
        },
      },
    },
    {
      path: "/sync-platforms/create",
      component: PlatformForm,
      meta: {
        title: "添加平台",
      },
    },
    {
      path: "/sync-platforms/:name/edit",
      component: PlatformForm,
      meta: {
        title: "编辑平台",
      },
    },
  ],
  extensionPoints: {
    "post:editor:extension:toolbar:create": (post) => {
      return [
        {
          id: "sync-to-platforms",
          label: "同步到其他平台",
          icon: "ri:share-circle-line",
          action: () => {
            // TODO: 打开同步对话框
            console.log("Sync post:", post);
          },
        },
      ];
    },
  },
});
