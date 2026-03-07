import { definePlugin } from "@halo-dev/ui-shared";
import { markRaw } from "vue";
import { IconRiShareLine } from "@iconify-prerendered/vue-remix-icon";
import PlatformList from "./views/PlatformList.vue";
import TaskList from "./views/TaskList.vue";

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/multi-publisher",
        name: "MultiPublisher",
        redirect: "/multi-publisher/platforms",
        meta: {
          title: "多平台同步",
          menu: {
            name: "多平台同步",
            group: "content",
            icon: markRaw(IconRiShareLine),
            priority: 0
          }
        },
        children: [
          {
            path: "platforms",
            name: "PublisherPlatforms",
            component: PlatformList,
            meta: {
              title: "平台管理"
            }
          },
          {
            path: "tasks",
            name: "PublisherTasks",
            component: TaskList,
            meta: {
              title: "同步任务"
            }
          }
        ]
      }
    }
  ],
  extensionPoints: {}
});
