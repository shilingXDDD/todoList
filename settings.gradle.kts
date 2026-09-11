pluginManagement {
    repositories {
        // ====== 国内镜像源（优先） ======
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")

        // ====== 公司内部仓库 ======
        maven("https://nexus20.tclking.com/repository/proxy-nexus-maven-pub/")
        maven {
            url = uri("http://10.92.35.98:8081/nexus/repository/maven-public/")
            isAllowInsecureProtocol = true
        }

        // ====== 官方仓库（备选） ======
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.tct.sign-plugin") {
                useModule("com.tct.sign.plugin:autosign:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // ====== 国内镜像源（优先） ======
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")

        // ====== 公司内部仓库 ======
        maven("https://nexus20.tclking.com/repository/proxy-nexus-maven-pub/")
        maven {
            url = uri("http://10.92.35.98:8081/nexus/repository/maven-public/")
            isAllowInsecureProtocol = true
        }

        // ====== 官方仓库（备选） ======
        google()
        mavenCentral()
    }
}

rootProject.name = "todo-list"
include(":app")
