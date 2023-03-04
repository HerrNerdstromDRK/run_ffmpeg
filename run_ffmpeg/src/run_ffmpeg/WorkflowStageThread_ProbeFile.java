package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

public class WorkflowStageThread_ProbeFile extends WorkflowStageThread
{
	private transient MongoCollection< JobRecord_ProbeFile > jobRecord_ProbeFileInfoCollection = null ;
	
	public WorkflowStageThread_ProbeFile( final String threadName,
			Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< JobRecord_ProbeFile > jobRecord_ProbeFileInfoCollection )
	{
		super( threadName, log, common, masMDB ) ;
		this.jobRecord_ProbeFileInfoCollection = jobRecord_ProbeFileInfoCollection ;
	}
	
	@Override
	public void doAction()
	{
		// Get the jobrecord from the database.
		// No real way to do this without deleting jobs.
		// If I don't delete the job, then this thread will infinitely spin on this one item.
		JobRecord_ProbeFile theJob = jobRecord_ProbeFileInfoCollection.findOneAndDelete( null ) ;
		if( null == theJob )
		{
			// Nothing to do.
			return ;
		}
		log.info( "Got job: " + theJob.toString() ) ;
		
		File theFile = new File( theJob.fileNameWithPath ) ;
		ProbeDirectories pd = new ProbeDirectories( log, common, masMDB, masMDB.getProbeInfoCollection() ) ;
		pd.probeFileAndUpdateDB( theFile ) ;
	}
	
}
