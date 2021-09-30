/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.gradle.plugin;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.jkube.kit.build.service.docker.config.DockerMachineConfiguration;
import org.eclipse.jkube.kit.common.JavaProject;
import org.eclipse.jkube.kit.common.KitLogger;
import org.eclipse.jkube.kit.common.ResourceFileType;
import org.eclipse.jkube.kit.common.util.OpenshiftHelper;
import org.eclipse.jkube.kit.common.util.ResourceClassifier;
import org.eclipse.jkube.kit.config.access.ClusterConfiguration;
import org.eclipse.jkube.kit.config.image.ImageConfiguration;
import org.eclipse.jkube.kit.config.image.build.JKubeBuildStrategy;
import org.eclipse.jkube.kit.config.image.build.RegistryAuthConfiguration;
import org.eclipse.jkube.kit.config.resource.MappingConfig;
import org.eclipse.jkube.kit.config.resource.PlatformMode;
import org.eclipse.jkube.kit.config.resource.ProcessorConfig;
import org.eclipse.jkube.kit.config.resource.ResourceConfig;
import org.eclipse.jkube.kit.config.resource.RuntimeMode;

import groovy.lang.Closure;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.codehaus.plexus.util.StringUtils;
import org.gradle.api.provider.Property;

import static org.eclipse.jkube.gradle.plugin.GroovyUtil.closureTo;
import static org.eclipse.jkube.gradle.plugin.GroovyUtil.invokeOrParseClosureList;
import static org.eclipse.jkube.kit.build.service.docker.DockerAccessFactory.DockerAccessContext.DEFAULT_MAX_CONNECTIONS;

/**
 * <pre>
 * {@code
 * kubernetes {
 *     useColor = false
 *     offline = false
 *     buildStrategy = 'jib'
 *     access {
 *         namespace = 'default'
 *     }
 *     enricher {
 *         excludes = ['jkube-expose']
 *     }
 *     resources {
 *         controllerName ='test'
 *         configMap {
 *             name = 'configMap-name'
 *             entries = [
 *                 [name: 'test', value: 'value']
 *             ]
 *         }
 *     }
 *     images {
 *         image2 { name = 'registry/image2:tag' }
 *         image1 {
 *             name = 'registry/image:tag'
 *             build {
 *                 from = 'busybox'
 *                 assembly {
 *                   layers = [
 *                     {
 *                       id = "custom-assembly-for-micronaut"
 *                       files = [
 *                         {
 *                           source = file('.')
 *                           outputDirectory = '.'
 *                         },
 *                         [source: file('other'), outputDirectory: file('output')]
 *                       ]
 *                     }
 *                   ]
 *                 }
 *             }
 *         }
 *     }
 * }
 *
 * openshift {
 *  useColor = true
 * }
 * }
 * </pre>
 *
 * Nested config
 */
@SuppressWarnings({"java:S1104", "java:S1845"})
public abstract class KubernetesExtension {

  private static final boolean DEFAULT_OFFLINE = false;
  private static final Path DEFAULT_KUBERNETES_MANIFEST = Paths.get("META-INF", "jkube", "kubernetes.yml");
  private static final Path DEFAULT_JSON_LOG_DIR = Paths.get("jkube","applyJson");
  private static final Path DEFAULT_RESOURCE_SOURCE_DIR = Paths.get("src", "main", "jkube");
  private static final Path DEFAULT_RESOURCE_TARGET_DIR = Paths.get("META-INF", "jkube");
  private static final String DEFAULT_WORK_DIR = "jkube";

  public transient JavaProject javaProject;

  public abstract Property<Boolean> getOffline();

  public abstract Property<Boolean> getUseColor();

  public abstract Property<Integer> getMaxConnections();

  public abstract Property<String> getFilter();

  public abstract Property<String> getApiVersion();

  public abstract Property<String> getBuildRecreate();

  public abstract Property<String> getImagePullPolicy();

  public abstract Property<String> getAutoPull();

  public abstract Property<String> getDockerHost();

  public abstract Property<String> getCertPath();

  public abstract Property<String> getMinimalApiVersion();

  public abstract Property<Boolean> getSkipMachine();

  public abstract Property<Boolean> getForcePull();

  public abstract Property<Boolean> getSkipExtendedAuth();

  public abstract Property<String> getPullRegistry();

  public abstract Property<String> getBuildSourceDirectory();

  public abstract Property<String> getBuildOutputDirectory();

  public abstract Property<File> getResourceTargetDirectory();

  public abstract Property<File> getResourceSourceDirectory();

  public abstract Property<String> getResourceEnvironment();

  public abstract Property<Boolean> getUseProjectClassPath();

  public abstract Property<File> getWorkDirectory();

  public abstract Property<Boolean> getSkipResourceValidation();

  public abstract Property<Boolean> getFailOnValidationError();

  public abstract Property<String> getProfile();

  public abstract Property<String> getNamespace();

  public abstract Property<Boolean> getMergeWithDekorate();

  public abstract Property<Boolean> getInterpolateTemplateParameters();

  public abstract Property<Boolean> getSkip();

  public abstract Property<Boolean> getLogFollow();

  public abstract Property<String> getLogContainerName();

  public abstract Property<String> getLogPodName();

  public abstract Property<File> getKubernetesManifest();

  public abstract Property<Boolean> getRecreate();

  public abstract Property<Boolean> getSkipApply();

  public abstract Property<Boolean> getCreateNewResources();

  public abstract Property<Boolean> getRollingUpgrades();

  public abstract Property<Boolean> getRollingUpgradePreserveScale();

  public abstract Property<Boolean> getFailOnNoKubernetesJson();

  public abstract Property<Boolean> getServicesOnly();

  public abstract Property<Boolean> getIgnoreServices();

  public abstract Property<Boolean> getDeletePodsOnReplicationControllerUpdate();

  public abstract Property<File> getJsonLogDir();

  public abstract Property<Integer> getServiceUrlWaitTimeSeconds();

  public abstract Property<Boolean> getSkipPush();

  public abstract Property<String> getPushRegistry();

  public abstract Property<Boolean> getSkipTag();

  public abstract Property<Integer> getPushRetries();

  public JKubeBuildStrategy buildStrategy;

  public abstract Property<String> getSourceDirectory();

  public abstract Property<String> getOutputDirectory();

  public abstract Property<String> getRegistry();

  public abstract Property<Boolean> getProcessTemplatesLocally();

  public abstract Property<Boolean> getIgnoreRunningOAuthClients();

  public ClusterConfiguration access;

  public ResourceConfig resources;

  public ProcessorConfig enricher;

  public ProcessorConfig generator;

  public List<ImageConfiguration> images;

  public DockerMachineConfiguration machine;

  public RegistryAuthConfiguration authConfig;

  public List<MappingConfig> mappings;

  public ResourceFileType resourceFileType;

  public RuntimeMode getRuntimeMode() {
    return RuntimeMode.KUBERNETES;
  }

  public JKubeBuildStrategy getBuildStrategy() {
    return buildStrategy != null ? buildStrategy : JKubeBuildStrategy.docker;
  }

  public boolean isDockerAccessRequired() {
    return getBuildStrategyOrDefault() != JKubeBuildStrategy.jib;
  }

  public PlatformMode getPlatformMode() {
    return PlatformMode.kubernetes;
  }

  public ResourceClassifier getResourceClassifier() {
    return ResourceClassifier.KUBERNETES;
  }

  public ResourceFileType getResourceFileType() {
    return resourceFileType != null ? resourceFileType : ResourceFileType.yaml;
  }

  public boolean isSupportOAuthClients() {
    return false;
  }

  public void access(Closure<?> closure) {
    access = closureTo(closure, ClusterConfiguration.class);
  }

  public void resources(Closure<?> closure) {
    resources = closureTo(closure, ResourceConfig.class);
  }

  public void enricher(Closure<?> closure) {
    enricher = closureTo(closure, ProcessorConfig.class);
  }

  public void generator(Closure<?> closure) {
    generator = closureTo(closure, ProcessorConfig.class);
  }

  /**
   * Provide support for image configurations as:
   *
   * <pre>
   * {@code
   * images {
   *   image1 {...}
   *   image2 {...}
   * }
   * }
   * </pre>
   *
   * @param closure The closure to unmarshal containing a Map of ImageConfiguration
   */
  public void images(Closure<?> closure) {
    invokeOrParseClosureList(closure, ImageConfiguration.class).ifPresent(i -> this.images = i);
  }

  /**
   * Provide support for image configurations as:
   *
   * <pre>
   * {@code
   * images ([
   *   {...},
   *   {...}
   * ])
   * }
   * </pre>
   *
   * @param closures The list of closures to unmarshal with individual ImageConfiguration
   */
  public void images(List<Closure<?>> closures) {
    images = closures.stream().map(c -> closureTo(c, ImageConfiguration.class)).collect(Collectors.toList());
  }

  /**
   * Provide support for image configurations as:
   *
   * <pre>
   * {@code
   * images {
   *   image {...}
   *   image {...}
   * }
   * }
   * </pre>
   *
   * @param closure The closure to unmarshal with an ImageConfiguration
   */
  public void image(Closure<?> closure) {
    if (images == null) {
      images = new ArrayList<>();
    }
    images.add(closureTo(closure, ImageConfiguration.class));
  }

  public void machine(Closure<?> closure) {
    machine = closureTo(closure, DockerMachineConfiguration.class);
  }

  public void authConfig(Closure<?> closure) {
    authConfig = closureTo(closure, RegistryAuthConfiguration.class);
  }

  public void mappings(Closure<?> closure) {
    invokeOrParseClosureList(closure, MappingConfig.class).ifPresent(i -> this.mappings = i);
  }

  public void mappings(List<Closure<?>> closures) {
    mappings = closures.stream().map(c -> closureTo(c, MappingConfig.class)).collect(Collectors.toList());
  }

  public void mapping(Closure<?> closure) {
    if (mappings == null) {
      mappings = new ArrayList<>();
    }
    mappings.add(closureTo(closure, MappingConfig.class));
  }

  public boolean getOfflineOrDefault() {
    return getOrDefaultBoolean("jkube.offline", this::getOffline, DEFAULT_OFFLINE);
  }

  public boolean getUseProjectClassPathOrDefault() {
    return getOrDefaultBoolean("jkube.useProjectClasspath", this::getUseProjectClassPath, false);
  }

  public boolean getFailOnValidationErrorOrDefault() {
    return getOrDefaultBoolean("jkube.failOnValidationError", this::getFailOnValidationError, false);
  }

  public boolean getMergeWithDekorateOrDefault() {
    return getOrDefaultBoolean("jkube.mergeWithDekorate", this::getMergeWithDekorate, false);
  }

  public boolean getInterpolateTemplateParametersOrDefault() {
    return getOrDefaultBoolean("jkube.interpolateTemplateParameters", this::getInterpolateTemplateParameters, true);
  }

  public boolean getSkipResourceValidationOrDefault() {
    return getOrDefaultBoolean("jkube.skipResourceValidation", this::getSkipResourceValidation, false);
  }

  public boolean getLogFollowOrDefault() {
    return getOrDefaultBoolean("jkube.log.follow", this::getLogFollow, true);
  }

  public File getManifest(KitLogger kitLogger, KubernetesClient kubernetesClient) {
    if (OpenshiftHelper.isOpenShift(kubernetesClient)) {
      kitLogger.warn("OpenShift cluster detected, using Kubernetes manifests");
      kitLogger.warn("Switch to openshift-gradle-plugin in case there are any problems");
    }
    return getKubernetesManifestOrDefault();
  }

  public boolean getRecreateOrDefault() {
    return getOrDefaultBoolean("jkube.recreate", this::getRecreate, false);
  }

  public boolean getSkipApplyOrDefault() {
    return getOrDefaultBoolean("jkube.skip.apply", this::getSkipApply, false);
  }

  public boolean getFailOnNoKubernetesJsonOrDefault() {
    return getOrDefaultBoolean("jkube.deploy.failOnNoKubernetesJson", this::getFailOnNoKubernetesJson, false);
  }

  public boolean getCreateNewResourcesOrDefault() {
    return getOrDefaultBoolean("jkube.deploy.create", this::getCreateNewResources, true);
  }

  public boolean getServicesOnlyOrDefault() {
    return getOrDefaultBoolean("jkube.deploy.servicesOnly", this::getServicesOnly, false);
  }

  public boolean getIgnoreServicesOrDefault() {
    return getOrDefaultBoolean("jkube.deploy.ignoreServices", this::getIgnoreServices, false);
  }

  public File getJsonLogDirOrDefault() {
    return getOrDefaultFile("jkube.deploy.jsonLogDir", this::getJsonLogDir, javaProject.getBuildDirectory().toPath().resolve(DEFAULT_JSON_LOG_DIR).toFile());
  }

  public boolean getDeletePodsOnReplicationControllerUpdateOrDefault() {
    return getOrDefaultBoolean("jkube.deploy.deletePods", this::getDeletePodsOnReplicationControllerUpdate, true);
  }

  public boolean getRollingUpgradesOrDefault() {
    return getOrDefaultBoolean("jkube.rolling", this::getRollingUpgrades, false);
  }

  public Integer getServiceUrlWaitTimeSecondsOrDefault() {
    return getOrDefaultInteger("jkube.serviceUrl.waitSeconds", this::getServiceUrlWaitTimeSeconds, 5);
  }

  public File getKubernetesManifestOrDefault() {
    return getOrDefaultFile("jkube.kubernetesManifest", this::getKubernetesManifest, javaProject.getOutputDirectory().toPath().resolve(DEFAULT_KUBERNETES_MANIFEST).toFile());
  }

  public boolean getSkipOrDefault() {
    return getOrDefaultBoolean("jkube.skip", this::getSkip, false);
  }

  public boolean getIgnoreRunningOAuthClientsOrDefault() {
    return getOrDefaultBoolean("jkube.deploy.ignoreRunningOAuthClients", this::getIgnoreRunningOAuthClients, true);
  }

  public boolean getProcessTemplatesLocallyOrDefault() {
    return getOrDefaultBoolean("jkube.deploy.processTemplatesLocally", this::getProcessTemplatesLocally, true);
  }

  public boolean getRollingUpgradePreserveScaleOrDefault() {
    return getOrDefaultBoolean("jkube.rolling.preserveScale", this::getRollingUpgradePreserveScale, false);
  }

  public boolean getSkipPushOrDefault() {
    return getOrDefaultBoolean("jkube.skip.push", this::getSkipPush, false);
  }

  public boolean getSkipTagOrDefault() {
    return getOrDefaultBoolean("jkube.skip.tag", this::getSkipTag, false);
  }

  public Integer getPushRetriesOrDefault() {
    return getOrDefaultInteger("jkube.docker.push.retries", this::getPushRetries, 0);
  }

  public boolean getSkipExtendedAuthOrDefault() {
    return getOrDefaultBoolean("jkube.docker.skip.extendedAuth", this::getSkipExtendedAuth, false);
  }

  public boolean getUseColorOrDefault() {
    return getOrDefaultBoolean("jkube.useColor", this::getUseColor, true);
  }

  public Integer getMaxConnectionsOrDefault() {
    return getOrDefaultInteger("jkube.docker.maxConnections", this::getMaxConnections, DEFAULT_MAX_CONNECTIONS);
  }

  public String getFilterOrDefault() {
    return getOrDefaultString("jkube.image.filter", this::getFilter, null);
  }

  public String getApiVersionOrDefault() {
    return getOrDefaultString("jkube.docker.apiVersion", this::getApiVersion, null);
  }

  public String getImagePullPolicyOrDefault() {
    return getOrDefaultString("jkube.docker.imagePullPolicy", this::getImagePullPolicy, null);
  }

  public String getAutoPullOrDefault() {
    return getOrDefaultString("jkube.docker.autoPull", this::getAutoPull, null);
  }

  public String getDockerHostOrDefault() {
    return getOrDefaultString("jkube.docker.host", this::getDockerHost, null);
  }

  public String getCertPathOrDefault() {
    return getOrDefaultString("jkube.docker.certPath", this::getCertPath, null);
  }

  public boolean getSkipMachineOrDefault() {
    return getOrDefaultBoolean("jkube.docker.skip.machine", this::getSkipMachine, false);
  }

  public boolean getForcePullOrDefault() {
    return getOrDefaultBoolean("jkube.build.forcePull", this::getForcePull, false);
  }

  public String getRegistryOrDefault() {
    return getOrDefaultString("jkube.docker.registry", this::getRegistry, "docker.io");
  }

  public String getBuildRecreateOrDefault() {
    return getOrDefaultString("jkube.build.recreate", this::getBuildRecreate, "none");
  }

  public String getPullRegistryOrDefault() {
    return getOrDefaultString("jkube.docker.pull.registry", this::getPullRegistry, getRegistryOrDefault());
  }

  public String getBuildSourceDirectoryOrDefault() {
    return getOrDefaultString("jkube.build.source.dir", this::getBuildSourceDirectory, "src/main/docker");
  }

  public String getBuildOutputDirectoryOrDefault() {
    return getOrDefaultString("jkube.build.target.dir", this::getBuildOutputDirectory, "build/docker");
  }

  public File getResourceSourceDirectoryOrDefault() {
    return getOrDefaultFile("jkube.resourceDir", this::getResourceSourceDirectory, javaProject.getBaseDirectory().toPath().resolve(DEFAULT_RESOURCE_SOURCE_DIR).toFile());
  }

  public File getResourceTargetDirectoryOrDefault() {
    return getOrDefaultFile("jkube.targetDir", this::getResourceTargetDirectory, javaProject.getOutputDirectory().toPath().resolve(DEFAULT_RESOURCE_TARGET_DIR).toFile());
  }

  public String getResourceEnvironmentOrDefault() {
    return getOrDefaultString("jkube.environment", this::getResourceEnvironment, null);
  }

  public File getWorkDirectoryOrDefault() {
    return getOrDefaultFile("jkube.workDir", this::getWorkDirectory, javaProject.getBuildDirectory().toPath().resolve(DEFAULT_WORK_DIR).toFile());
  }

  public String getProfileOrDefault() {
    return getOrDefaultString("jkube.profile", this::getProfile, null);
  }

  public String getNamespaceOrDefault() {
    return getOrDefaultString("jkube.namespace", this::getNamespace, null);
  }

  public String getLogPodNameOrDefault() {
    return getOrDefaultString("jkube.log.pod", this::getLogPodName, null);
  }

  public String getLogContainerNameOrDefault() {
    return getOrDefaultString("jkube.log.container", this::getLogContainerName, null);
  }

  public JKubeBuildStrategy getBuildStrategyOrDefault() {
    final String propValue = javaProject.getProperties().getProperty("jkube.build.strategy");
    if (StringUtils.isNotBlank(propValue)) {
      return JKubeBuildStrategy.valueOf(propValue);
    }
    return getBuildStrategy();
  }

  public ResourceFileType getResourceFileTypeOrDefault() {
    final String propValue = javaProject.getProperties().getProperty("jkube.resourceType");
    if (StringUtils.isNotBlank(propValue)) {
      return ResourceFileType.valueOf(propValue);
    }
    return getResourceFileType();
  }

  protected boolean getOrDefaultBoolean(String property, Supplier<Property<Boolean>> dslGetter, boolean defaultValue) {
    final String propValue = javaProject.getProperties().getProperty(property);
    if (StringUtils.isNotBlank(propValue)) {
      return Boolean.parseBoolean(propValue);
    }
    return dslGetter.get().getOrElse(defaultValue);
  }

  protected File getOrDefaultFile(String property, Supplier<Property<File>> dslGetter, File defaultValue) {
    final String propValue = javaProject.getProperties().getProperty(property);
    if (StringUtils.isNotBlank(propValue)) {
      return javaProject.getBaseDirectory().toPath().resolve(propValue).toFile();
    }
    return dslGetter.get().getOrElse(defaultValue);
  }

  protected Integer getOrDefaultInteger(String property, Supplier<Property<Integer>> dslGetter, Integer defaultValue) {
    final String propValue = javaProject.getProperties().getProperty(property);
    if (StringUtils.isNotBlank(propValue)) {
      return Integer.parseInt(propValue);
    }
    return dslGetter.get().getOrElse(defaultValue);
  }

  protected String getOrDefaultString(String property, Supplier<Property<String>> dslGetter, String defaultValue) {
    final String propValue = javaProject.getProperties().getProperty(property);
    if (StringUtils.isNotBlank(propValue)) {
      return propValue;
    }
    return dslGetter.get().getOrElse(defaultValue);
  }
}
