package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

public class WorkflowStageThread_ProbeFile extends WorkflowStageThread
{
	private transient MongoCollection< JobRecord_ProbeFile > jobRecord_ProbeFileInfoCollection = null ;
	private transient MongoCollection< JobRecord_UpdateCorrelatedFile > jobRecord_UpdateCorrelatedFileInfoCollection = null ;
	
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
		FFmpegProbeResult theProbeResult = pd.probeFileAndUpdateDB( theFile ) ;
		
		// Build the job to build the movie and show index.
		JobRecord_UpdateCorrelatedFile jobRecord_UpdateCorrelatedFile = new JobRecord_UpdateCorrelatedFile(
				theJob.getFileNameWithPath(), theJob.getMovieAndShowInfo_id(), theProbeResult.get_id() ) ;
		jobRecord_UpdateCorrelatedFileInfoCollection.insertOne( jobRecord_UpdateCorrelatedFile ) ;
	}
	
}
