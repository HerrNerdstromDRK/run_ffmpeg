package run_ffmpeg;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.mongodb.MongoClient; 
import com.mongodb.MongoCredential;  

/**
 * Class that provides instances that interact with the MoviesAndShows database.
 * This class provides basic login and interaction services, plus some larger
 * database functions, but generally does not manage the data in the database.
 * @author Dan
 */
public class MoviesAndShowsMongoDB
{
	private MongoClient persistentMongoClient = null ;
	private MongoDatabase persistentDatabaseHandle = null ;
	private final String probeInfoCollectionName = "ProbeInfo" ;
	private final String movieAndShowInfoCollectionName = "MovieAndShowInfo" ;

	/// File name to which to log activities for this application.
	static private final String logFileName = "log_extract_pgs.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	static private final String stopFileName = "C:\\Temp\\stop_database.txt" ;
	static private final String pathToFFPROBE = run_ffmpeg.pathToFFPROBE ;

	/// Set testMode to true to prevent mutations
	static private boolean testMode = true ;

	/**
	 * Create a new instance of this class.
	 * Setup basic configuration options and login to the database.
	 */
	public MoviesAndShowsMongoDB()
	{
		run_ffmpeg.testMode = testMode ;
		run_ffmpeg.openLogFile( logFileName ) ;
		loginAndConfigureDatabase() ;
	}

	/**
	 * Return a handle to the logged in database.
	 * If not already logged in, do so now.
	 * @return
	 */
	public MongoDatabase getDatabase()
	{
		if( null == persistentDatabaseHandle )
		{
			loginAndConfigureDatabase() ;
		}
		return persistentDatabaseHandle ;
	}

	/**
	 * Login to the database and return the db instance.
	 * @return
	 */
	private void loginAndConfigureDatabase()
	{
		if( persistentDatabaseHandle != null )
		{
			// Already configured.
			return ;
		}
		// Creating a MongoClient and connect to the database server 
		//	      MongoClient mongo = new MongoClient( "inventory.t43ck.mongodb.net" , 8888 ); 
		persistentMongoClient = new MongoClient( "localhost" , 27017 );
		
		// Setup the providers for passing Plain Old Java Objects (POJOs) to and from the database
		CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic( true ).build();
		CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

		// Login to the database
		MongoCredential credential = MongoCredential.createCredential("dan", "MoviesAndShows", 
				"BqQyH2r5xJuNu2A".toCharArray()); 
		System.out.println("loginToDatabase> Connected to the database successfully" );  

		// Configure the database to use the POJO provider and retrieve the handle
		persistentDatabaseHandle = persistentMongoClient.getDatabase( "MoviesAndShows" ).withCodecRegistry( pojoCodecRegistry ) ;
	}

	public MongoCollection< FFmpegProbeResult > getProbeInfoCollection()
	{	
		MongoCollection< FFmpegProbeResult > theCollection = persistentDatabaseHandle.getCollection( probeInfoCollectionName,
				FFmpegProbeResult.class ) ;
		return theCollection ;
	}

	public MongoCollection< MovieAndShowInfo > getMovieAndShowInfoCollection()
	{	
		MongoCollection< MovieAndShowInfo > theCollection = persistentDatabaseHandle.getCollection( movieAndShowInfoCollectionName,
				MovieAndShowInfo.class ) ;
		return theCollection ;
	}

	/*
	      System.out.println("Credentials ::"+ credential);     
			System.out.println("Collection MoviesAndShows selected successfully");
			Document document1 = new Document("title", "MongoDB")
			.append("description", "database")
			.append("likes", 100)
			.append("url", "http://www.tutorialspoint.com/mongodb/")
			.append("by", "tutorials point");
			Document document2 = new Document("title", "RethinkDB")
			.append("description", "database")
			.append("likes", 200)
			.append("url", "http://www.tutorialspoint.com/rethinkdb/")
			.append("by", "tutorials point");
			List<Document> list = new ArrayList<Document>();
			list.add(document1);
			list.add(document2);
			collection.insertMany(list);
			// Getting the iterable object
			FindIterable<Document> iterDoc = collection.find();
			int i = 1;
			// Getting the iterator
			Iterator it = iterDoc.iterator();
			while (it.hasNext()) {
				System.out.println(it.next());
				i++;
			}
	   } 
	 */

	static void out( final String outputMe )
	{
		run_ffmpeg.out( outputMe ) ;
	}

	static void log( final String logMe )
	{
		run_ffmpeg.log( logMe ) ;
	}

}
