
//Single Author info:
//yjkamdar Yash J Kamdar

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/*
 * Main class of the TFIDF MapReduce implementation.
 * Author: Yash Kamdar
 * Date:   10/28/2018
 */
public class TFIDF {

    public static void main(String[] args) throws Exception {
	// Check for correct usage
	if (args.length != 1) {
	    System.err.println("Usage: TFIDF <input dir>");
	    System.exit(1);
	}

	// Create configuration
	Configuration conf = new Configuration();

	// Input and output paths for each job
	Path inputPath = new Path(args[0]);
	Path wcInputPath = inputPath;
	Path wcOutputPath = new Path("output/WordCount");
	Path dsInputPath = wcOutputPath;
	Path dsOutputPath = new Path("output/DocSize");
	Path tfidfInputPath = dsOutputPath;
	Path tfidfOutputPath = new Path("output/TFIDF");

	// Get/set the number of documents (to be used in the TFIDF MapReduce job)
	FileSystem fs = inputPath.getFileSystem(conf);
	FileStatus[] stat = fs.listStatus(inputPath);
	String numDocs = String.valueOf(stat.length);
	conf.set("numDocs", numDocs);

	// Delete output paths if they exist
	FileSystem hdfs = FileSystem.get(conf);
	if (hdfs.exists(wcOutputPath))
	    hdfs.delete(wcOutputPath, true);
	if (hdfs.exists(dsOutputPath))
	    hdfs.delete(dsOutputPath, true);
	if (hdfs.exists(tfidfOutputPath))
	    hdfs.delete(tfidfOutputPath, true);

	// Create and execute Word Count job
	Job wcJob = Job.getInstance(conf, "word count");
	wcJob.setJarByClass(TFIDF.class);
	wcJob.setMapperClass(WCMapper.class);
	wcJob.setReducerClass(WCReducer.class);
	wcJob.setOutputKeyClass(Text.class);
	wcJob.setOutputValueClass(IntWritable.class);
	FileInputFormat.addInputPath(wcJob, wcInputPath);
	FileOutputFormat.setOutputPath(wcJob, wcOutputPath);
	wcJob.waitForCompletion(true);

	// Create and execute Document Size job
	Job dsJob = Job.getInstance(conf, "document size");
	dsJob.setJarByClass(TFIDF.class);
	dsJob.setMapperClass(DSMapper.class);
	dsJob.setReducerClass(DSReducer.class);
	dsJob.setOutputKeyClass(Text.class);
	dsJob.setOutputValueClass(Text.class);
	FileInputFormat.addInputPath(dsJob, dsInputPath);
	FileOutputFormat.setOutputPath(dsJob, dsOutputPath);
	dsJob.waitForCompletion(true);

	// Create and execute TFIDF job
	Job tfidfJob = Job.getInstance(conf, "tfidf");
	tfidfJob.setJarByClass(TFIDF.class);
	tfidfJob.setMapperClass(TFIDFMapper.class);
	tfidfJob.setReducerClass(TFIDFReducer.class);
	tfidfJob.setOutputKeyClass(Text.class);
	tfidfJob.setOutputValueClass(Text.class);
	FileInputFormat.addInputPath(tfidfJob, tfidfInputPath);
	FileOutputFormat.setOutputPath(tfidfJob, tfidfOutputPath);
	tfidfJob.waitForCompletion(true);

    }

    /*
     * Creates a (key,value) pair for every word in the document
     *
     * Input: ( byte offset , contents of one line ) Output: ( (word@document) , 1 )
     *
     * word = an individual word in the document document = the filename of the
     * document
     */
    public static class WCMapper extends Mapper<Object, Text, Text, IntWritable> {

	public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
	    // Get the file name
	    String fName = ((FileSplit) context.getInputSplit()).getPath().getName();

	    // split the file input text into array of words
	    String[] words = value.toString().split(" ");
	    for (int i = 0; i < words.length; i++) {
		// Map all the words in a file into the context
		context.write(new Text(words[i] + "@" + fName), new IntWritable(1));
	    }
	}
    }

    /*
     * For each identical key (word@document), reduces the values (1) into a sum
     * (wordCount)
     *
     * Input: ( (word@document) , 1 ) Output: ( (word@document) , wordCount )
     *
     * wordCount = number of times word appears in document
     */
    public static class WCReducer extends Reducer<Text, IntWritable, Text, IntWritable> {

	public void reduce(Text key, Iterable<IntWritable> values, Context context)
		throws IOException, InterruptedException {
	    int wc = 0;

	    // Calculate the total no. of words in each doc
	    for (IntWritable val : values) {
		wc += val.get();
	    }
	    // Save the generated word count as value for the keyword
	    context.write(key, new IntWritable(wc));
	}
    }

    /*
     * Rearranges the (key,value) pairs to have only the document as the key
     *
     * Input: ( (word@document) , wordCount ) Output: ( document , (word=wordCount)
     * )
     */
    public static class DSMapper extends Mapper<Object, Text, Text, Text> {

	public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
	    String[] in = value.toString().split("\t");
	    String[] keys = in[0].split("@");
	    // map the doc name as the key to the value of word and its count in that doc
	    context.write(new Text(keys[1]), new Text(keys[0] + "=" + in[1]));
	}
    }

    /*
     * For each identical key (document), reduces the values (word=wordCount) into a
     * sum (docSize)
     *
     * Input: ( document , (word=wordCount) ) Output: ( (word@document) ,
     * (wordCount/docSize) )
     *
     * docSize = total number of words in the document
     */
    public static class DSReducer extends Reducer<Text, Text, Text, Text> {

	public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
	    // DocSize counter
	    int ds = 0;

	    String fname = key.toString();
	    HashMap<String, String> words = new HashMap<>();

	    // Calculate the total number of words in the doc
	    for (Text val : values) {
		String[] vals = val.toString().split("=");
		ds += Integer.valueOf(vals[1]);
		words.put(vals[0], vals[1]);
	    }

	    // Save the generated word count/doc size as value for the keyword of that word
	    // in the specific file
	    for (String word : words.keySet()) {
		context.write(new Text(word + "@" + fname), new Text(words.get(word) + "/" + ds));
	    }
	}
    }

    /*
     * Rearranges the (key,value) pairs to have only the word as the key
     * 
     * Input: ( (word@document) , (wordCount/docSize) ) Output: ( word ,
     * (document=wordCount/docSize) )
     */
    public static class TFIDFMapper extends Mapper<Object, Text, Text, Text> {

	public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
	    String[] in = value.toString().split("\t");
	    String[] keys = in[0].split("@");
	    // Map the word as key to the filename and its corresponding keyword count to ds
	    context.write(new Text(keys[0]), new Text(keys[1] + "=" + in[1]));
	}
    }

    /*
     * For each identical key (word), reduces the values
     * (document=wordCount/docSize) into a the final TFIDF value (TFIDF). Along the
     * way, calculates the total number of documents and the number of documents
     * that contain the word.
     * 
     * Input: ( word , (document=wordCount/docSize) ) Output: ( (document@word) ,
     * TFIDF )
     *
     * numDocs = total number of documents numDocsWithWord = number of documents
     * containing word TFIDF = (wordCount/docSize) * ln(numDocs/numDocsWithWord)
     *
     * Note: The output (key,value) pairs are sorted using TreeMap ONLY for grading
     * purposes. For extremely large datasets, having a for loop iterate through all
     * the (key,value) pairs is highly inefficient!
     */
    public static class TFIDFReducer extends Reducer<Text, Text, Text, Text> {

	private static int numDocs;
	private Map<Text, Text> tfidfMap = new HashMap<>();

	// gets the numDocs value and stores it
	protected void setup(Context context) throws IOException, InterruptedException {
	    Configuration conf = context.getConfiguration();
	    numDocs = Integer.parseInt(conf.get("numDocs"));
	}

	public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
	    HashMap<String, String> docMap = new HashMap<>();

	    // Calculate the number of files with that word
	    int numDocsWithWord = 0;
	    for (Text val : values) {
		String[] valSplit = val.toString().split("=");
		numDocsWithWord += 1;
		docMap.put(valSplit[0], valSplit[1]);
	    }

	    for (String doc : docMap.keySet()) {
		String[] counts = docMap.get(doc).split("/");
		double wc = Double.valueOf(counts[0]);
		double ds = Double.valueOf(counts[1]);

		// TFIDF computation
		double TFIDF = (wc / ds) * Math.log((double) numDocs / (double) numDocsWithWord);

		// map the doc & word as the key to the tfidf calculated above
		tfidfMap.put(new Text(doc + "@" + key.toString()), new Text(String.valueOf(TFIDF)));
	    }
	}

	// sorts the output (key,value) pairs that are contained in the tfidfMap
	protected void cleanup(Context context) throws IOException, InterruptedException {
	    Map<Text, Text> sortedMap = new TreeMap<Text, Text>(tfidfMap);
	    for (Text key : sortedMap.keySet()) {
		context.write(key, sortedMap.get(key));
	    }
	}

    }
}
