package dataset;


import dataset.callable.CommitAnalysis;
import dataset.callable.FixFinder;
import dataset.callable.OnlineAnalysis;
import dataset.model.Bug;
import dataset.model.Software;
import dataset.setup.Softwares;
import gitutils.gitUtilitaries.GitActions;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Dataset generator
 */
public class BugDatasetGenerator {
    private static final int NBTHREADS = 5;
    public final Software software;
    private final String localFolder;

    /**
     * constructor of the dataset
     * @param software to consider
     * @param localFolder folder in which to place it
     */
    public BugDatasetGenerator(Software software, String localFolder) {
        this.software = software;
        this.localFolder = localFolder;
    }

    /**
     * Method taking care of cloning the git
     * @throws GitAPIException
     * @throws IOException
     */
    private void gitImport() throws GitAPIException, IOException {
        new GitActions(software.getGitUrl(), localFolder);
    }

    /**
     * method to generate the dataset
     * @return list of bugs
     * @throws GitAPIException
     * @throws IOException
     */
    public List<Bug> retrieveBugs() throws GitAPIException, IOException {
        gitImport();
        File gitf = new File(localFolder, ".git");
        Git git = Git.open(gitf);
        Iterator<RevCommit> it = git.log().all().call().iterator();
        List<Bug> bugslikely = analyseCommits(it);
        List<Bug> realbugsIncomplete = onlineAnalysis(bugslikely);
        return retrieveAllBugs(realbugsIncomplete);
    }

    /**
     * Second analyse wheter the issue is a bug from the tracker and get the affected version
     * @param bugslikely
     * @return
     */
    private List<Bug> onlineAnalysis(List<Bug> bugslikely) {

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        CompletionService<Bug> completionService = new ExecutorCompletionService<>(executor);
        int count = 0;
        for (Bug bug : bugslikely) {
            OnlineAnalysis ca = new OnlineAnalysis(bug);
            completionService.submit(ca);
            count++;
        }
        List<Bug> commitBug = retrieveAsynchronousBugs(completionService, count);
        executor.shutdown();
        return commitBug;
    }


    /**
     * Finally retrieve all files that were modified to patch the bug
     * @param bugs
     * @return
     */
    private List<Bug> retrieveAllBugs(List<Bug> bugs) {

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(NBTHREADS);
        CompletionService<Bug> completionService = new ExecutorCompletionService<>(executor);
        int count = 0;
        for (Bug bug : bugs) {
            FixFinder ca = new FixFinder(bug, new GitActions(localFolder));
            completionService.submit(ca);
            count++;
        }
        List<Bug> commitBug = retrieveAsynchronousBugs(completionService, count);
        executor.shutdown();
        return commitBug;

    }

    /**
     * First analyze all commit hitory trying to find reference to bug issue
     * @param it
     * @return
     */
    private List<Bug> analyseCommits(Iterator<RevCommit> it) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(NBTHREADS);
        CompletionService<Bug> completionService = new ExecutorCompletionService<>(executor);
        int count = 0;
        while (it.hasNext()) {
            CommitAnalysis ca = new CommitAnalysis(it.next(), software.getAbreviation());
            completionService.submit(ca);
            count++;
        }
        List<Bug> commitBug = retrieveAsynchronousBugs(completionService, count);
        executor.shutdown();
        return commitBug;
    }

    /**
     * Method to retrive the bugs from the completion service
     * @param completionService
     * @param count
     * @return
     */
    private List<Bug> retrieveAsynchronousBugs(CompletionService<Bug> completionService, int count) {
        List<Bug> commitBug = new LinkedList<>();
        int received = 0;
        while (received < count) {
            try {
                Future<Bug> futurResult = completionService.take();
                Bug result = futurResult.get();
                received++;
                //System.out.println(received + "/" + count);
                if (result != null) {
                    commitBug.add(result);
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        return commitBug;
    }

    /**
     * Main for testing purposes
     * @param args
     */
    public static void main(String[] args) {
        for (Software software : Softwares.getAll()) {
            BugDatasetGenerator bugDatasetGenerator = new BugDatasetGenerator(software, "/Users/matthieu/Documents/programmation/exp/"+software.getAbreviation()+"/");
            try {
                List<Bug> bugs = bugDatasetGenerator.retrieveBugs();
                System.out.println("For " + software.getName() + ": " + bugs.size() + " found");
                BugDatasetGenerator.saveBugs(bugs,"/Users/matthieu/Documents/programmation/exp/"+software.getAbreviation()+"/bugs.obj");
            } catch (GitAPIException | IOException e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * Bug list save
     * @param bugs
     * @param file
     * @throws IOException
     */
    public static void saveBugs(List<Bug> bugs, String file) throws IOException {
        FileOutputStream fileOut = new FileOutputStream(file, false);
        ObjectOutputStream out = new ObjectOutputStream(fileOut);
        out.writeObject(bugs);
        out.close();
        fileOut.close();
    }

    /**
     * Bug list loader
     * @param file
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static List<Bug> loadBugs(String file) throws IOException, ClassNotFoundException {
        FileInputStream fileIn = new FileInputStream(file);
        ObjectInputStream in = new ObjectInputStream(fileIn);
        return (List<Bug>) in.readObject();
    }
}
