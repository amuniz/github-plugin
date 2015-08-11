package org.jenkinsci.plugins.github.webhook.subscriber;

import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;

import java.util.HashMap;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHEvent;

import com.cloudbees.jenkins.GitHubPushTrigger;
import com.cloudbees.jenkins.GitHubWebHookFullTest;

public class WebhookWorkflow {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void receivePushHookOnWorkflow() throws Exception {
        j.jenkins.getInjector().injectMembers(this);
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "Test Workflow");

        GitHubPushTrigger trigger = new GitHubPushTrigger();
        trigger.start(job, false);
        job.addTrigger(trigger);
        job.setDefinition(new CpsFlowDefinition("node {" +
            "git 'https://github.com/amuniz/github-plugin.git'" +
        "}"));

        // Trigger the build once to register SCMs
        WorkflowRun lastRun = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        // Testing hack! This will make the polling believe that there was remote changes to build
        lastRun.getActions(BuildData.class).get(0).buildsByBranchName = new HashMap<String, Build>();

        // Then simulate a GitHub push
        new DefaultPushGHEventSubscriber()
                .onEvent(GHEvent.PUSH, GitHubWebHookFullTest.classpath("payloads/push-wf.json"));
        Run build = waitForBuild(2, job);
        j.assertBuildStatusSuccess(build);
    }

    private Run waitForBuild(int n, Job job) throws InterruptedException {
        System.out.println("Waiting for build #" + n + " to appear and finish");
        int counter = 0;
        while (counter < 30) {
            Run b = job.getBuildByNumber(n);
            if (b != null && !b.isBuilding()) {
                return b;
            }
            Thread.sleep(2000);
            counter++;
        }
        return null;
    }

}