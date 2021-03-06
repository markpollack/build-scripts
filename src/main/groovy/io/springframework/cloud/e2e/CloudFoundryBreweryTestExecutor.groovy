package io.springframework.cloud.e2e

import io.springframework.cloud.common.SpringCloudJobs
import io.springframework.cloud.common.SpringCloudNotification
import io.springframework.common.job.BashCloudFoundry
import io.springframework.common.job.Cron
import io.springframework.common.job.JdkConfig
import io.springframework.common.job.TestPublisher
import javaposse.jobdsl.dsl.DslFactory
/**
 * @author Marcin Grzejszczak
 */
class CloudFoundryBreweryTestExecutor implements TestPublisher, JdkConfig, BreweryDefaults,
		BashCloudFoundry, Cron, SpringCloudJobs {

	private final DslFactory dsl

	CloudFoundryBreweryTestExecutor(DslFactory dsl) {
		this.dsl = dsl
	}

	void buildBreweryForDocsTests() {
		// Run acceptance tests - skip building, deploying to CF, add docsbrewing prefix to CF
		build('spring-cloud-brewery-for-docs-tests', 'spring-cloud-samples',
				'brewery', "runAcceptanceTests.sh --whattotest SLEUTH_STREAM --usecloudfoundry --cloudfoundryprefix docsbrewing --skipbuilding --skipdeployment", everyThreeHours())
	}

	protected void build(String description, String githubOrg, String projectName, String script, String cronExpr) {
		dsl.job("${description}-on-cf-e2e") {
			triggers {
				cron cronExpr
			}
			jdk jdk8()
			wrappers {
				environmentVariables([
						TEST_ZIPKIN_DEPENDENCIES: 'false',
				])
				timeout {
					noActivity(defaultInactivity())
					failBuild()
					writeDescription('Build failed due to timeout after {0} minutes of inactivity')
				}
			}
			scm {
				git {
					remote {
						url "https://github.com/$githubOrg/$projectName"
						branch 'master'
					}
					extensions {
						wipeOutWorkspace()
					}
				}
			}
			wrappers {
				timestamps()
				colorizeOutput()
				maskPasswords()
				credentialsBinding {
					usernamePassword(cfUsername(), cfPassword(), cfCredentialsId())
				}
				environmentVariables {
					env(cfSpacePropName(), cfSpace())
				}
			}
			steps {
				shell(cfScriptToExecute(script))
			}
			configure {
				SpringCloudNotification.cloudSlack(it as Node)
			}
			publishers {
				archiveArtifacts acceptanceTestReports()
				archiveArtifacts {
					pattern acceptanceTestSpockReports()
					allowEmpty()
				}
			}
		}
	}

}
