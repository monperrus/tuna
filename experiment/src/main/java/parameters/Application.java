package parameters;

import defectDataset.model.Software;
import defectDataset.setup.Softwares;
import gitUtilitaries.GitActions;
import modelling.util.assertion.Assert;
import modelling.util.assertion.FailureException;
import org.eclipse.jgit.api.errors.GitAPIException;
import tokenizer.file.AbstractFileTokenizer;
import tokenizer.file.java.exception.UnparsableException;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static tokenizer.file.JavaFileTokenizerFactory.*;

public class Application {
    private final String directory;

    private final Map<String, String> projects = new LinkedHashMap<>();
    private final Map<String, List<String>> projectsPaths = new LinkedHashMap<>();

    private static Map<String, AbstractFileTokenizer> createTokenizers() {
        Map<String, AbstractFileTokenizer> result = new LinkedHashMap<>();

        putTokenizer(result, utfTokenizer());
        putTokenizer(result, utfWocTokenizer());
        putTokenizer(result, lemmeTokenizer());
        putTokenizer(result, lemmeWocTokenizer());
        putTokenizer(result, depthFirstTokenizer());
        putTokenizer(result, breadthFirstTokenizer());
        putTokenizer(result, depthFirstPrunedTokenizer());
        putTokenizer(result, breadthFirstPrunedTokenizer());

        return result;
    }

    private static void putTokenizer(Map<String, AbstractFileTokenizer> map, AbstractFileTokenizer tokenizer) {
        map.put(tokenizer.getType(), tokenizer);
    }

    public Application(String path) {
        directory = path;
        for (Software software : Softwares.getAll()) {
            projects.put(software.getAbreviation(), software.getGitUrl());
        }
    }

    public static void main(String[] args) throws IOException {

        Application app = new Application();
        app.run();
    }

    private void run() throws IOException {
        // Load Path
        for (String project : projects.keySet()) {
            projectsPaths.put(project, loadPaths(project));
        }
        System.out.println("done with git");
        //Per Tokenizer Study
        for (Map.Entry<String, AbstractFileTokenizer> tokenizer : createTokenizers().entrySet()) {
            Map<String, List<Iterable<String>>> tokenizeContent = new LinkedHashMap<>();

            //For Each Project Parsing
            File v = new File(directory + tokenizer.getKey() + "_vocab" + ".csv");
            CSVWriter writerv = new CSVWriter(new FileWriter(v, false), ';');
            for (Map.Entry<String, List<String>> projectPaths : projectsPaths.entrySet()) {
                List<Iterable<String>> parsedContent = parse(projectPaths.getValue(), tokenizer.getValue());
                tokenizeContent.put(projectPaths.getKey(), parsedContent);
                VocabularyGrowthResult growthResult = VocabularyGrowth.run(parsedContent);
                growthResult.addToCSV(writerv, projectPaths.getKey());
            }
            System.out.println("done with " + tokenizer.getKey() + " vocabulary");

            writerv.close();

            //Then run Single and Cross Project Precision
            for (Map.Entry<String, List<Iterable<String>>> project : tokenizeContent.entrySet()) {
                runSingle(tokenizer.getKey(), project.getKey(), project.getValue());
                System.out.println("done with single for " + project.getKey());
                for (String testProject : projects.keySet()) {
                    if (!testProject.equals(project.getKey())) {
                        runCross(tokenizer.getKey(), project.getKey(), testProject, project.getValue(), tokenizeContent.get(testProject));
                        System.out.println("done with cross for " + project.getKey() + " to " + testProject);
                    }
                }
            }

        }


    }

    private void runCross(String tokenizer, String trainingProject, String testProject, List<Iterable<String>> train, List<Iterable<String>> test) {
        File f = new File(directory + tokenizer + "_" + trainingProject + "_to_" + testProject + ".csv");
        if (!f.exists()) {
            ExperimentSet experimentSet = new ExperimentSet(train, test);
            Map<Setup, Double> result = experimentSet.run();
            try {
                ResultToCSV.write(result, tokenizer + "_" + trainingProject + "_to_" + testProject, directory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void runSingle(String tokenizer, String project, List<Iterable<String>> value) {
        File f = new File(directory + tokenizer + "_" + project + ".csv");
        if (!f.exists()) {
            ExperimentSet experimentSet = new ExperimentSet(value);
            Map<Setup, Double> result = experimentSet.run();
            try {
                ResultToCSV.write(result, tokenizer + "_" + project, directory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<Iterable<String>> parse(Iterable<String> paths,
                                        AbstractFileTokenizer tokenizer) {
        List<Iterable<String>> result = new LinkedList<>();
        for (String path : paths) {
            try {
                Reader reader = new FileReader(new File(path));
                result.add(tokenizer.tokenize(reader));
            } catch (ParseProblemException ppe) {
                // It's ok, just ignore
            } catch (IOException ex) {
                Assert.shouldNeverGetHere();
            } catch (UnparsableException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    private List<String> loadPaths(String project) {
        return loadPaths(directory + project, projects.get(project));
    }

    private List<String> loadPaths(String directory, String gitURL) {
        GitActions git = new GitActions(gitURL,directory);
        try {
            return FilesOfInterest.list(directory, "java");
        } catch (IOException ex) {
            throw new FailureException(ex);
        }
    }

    private void updateLocalRepository(String path, String gitURL) {
        GithubImporter importer = new GithubImporter(gitURL, path);
        try {
            importer.updateRepo();
        } catch (IOException | GitAPIException ex) {
            ex.printStackTrace();
            // It's ok, just ignore
        }
    }


}