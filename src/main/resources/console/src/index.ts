import { definePlugin } from "@halo-dev/console-shared";
import PlatformList from "./PlatformList.vue";
import PlatformForm from "./PlatformForm.vue";

export default definePlugin({
  components: {},
  routes: [
    {
      path: "/sync-platforms",
      children: [
        {
          path: "",
          name: "SyncPlatforms",
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
          path: "create",
          name: "SyncPlatformCreate",
          component: PlatformForm,
          meta: {
            title: "添加平台",
          },
        },
        {
          path: ":name/edit",
          name: "SyncPlatformEdit",
          component: PlatformForm,
          meta: {
            title: "编辑平台",
          },
        },
      ],
    },
  ],
  extensionPoints: {
    "post:editor:action": (post: any) => {
      return [
        {
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
