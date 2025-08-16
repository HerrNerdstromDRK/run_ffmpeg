package run_ffmpeg;

import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

public class WorkflowStageThread_ProbeFile extends WorkflowStageThread
{
	private transient MongoCollection< JobRecord_ProbeFile > jobRecord_ProbeFileInfoCollection = null ;
	
	public WorkflowStageThread_ProbeFile( final String threadName,
			Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB )
	{
		super( threadName, log, common, masMDB ) ;
//		jobRecord_ProbeFileInfoCollection = masMDB.getJobRecord_ProbeFileInfoCollection() ;
//		jobRecord_UpdateCorrelatedFileInfoCollection = masMDB.getJobRecord_UpdateCorrelatedFileInfoCollectionName() ;
	}

	@Override
	public boolean doAction()
	{
		// Get the jobrecord from the database.
		// No real way to do this without deleting jobs.
		// If I don't delete the job, then this thread will infinitely spin on this one item.
		JobRecord_ProbeFile theJob = jobRecord_ProbeFileInfoCollection.findOneAndDelete( null ) ;
		if( null == theJob )
		{
			// Nothing to do.
			return false ;
		}
		log.info( "Got job: " + theJob.toString() ) ;
		
//		File theFile = new File( theJob.fileNameWithPath ) ;
//		ProbeDirectories pd = new ProbeDirectories( log, common, masMDB, masMDB.getProbeInfoCollection() ) ;
//		FFmpeg_ProbeResult theProbeResult = pd.probeFileAndUpdateDB( theFile ) ;
		
		return false ;
	}
	
}
