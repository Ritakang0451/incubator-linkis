/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package org.apache.linkis.manager.engineplugin.pipeline.executor

import java.util

import org.apache.linkis.common.utils.Logging
import org.apache.linkis.engineconn.computation.executor.execute.{ComputationExecutor, EngineExecutionContext}
import org.apache.linkis.engineconn.core.EngineConnObject
import org.apache.linkis.manager.common.entity.resource.{CommonNodeResource, LoadInstanceResource, NodeResource}
import org.apache.linkis.manager.engineplugin.common.conf.EngineConnPluginConf
import org.apache.linkis.manager.engineplugin.pipeline.exception.PipeLineErrorException
import org.apache.linkis.manager.label.entity.Label
import org.apache.linkis.protocol.engine.JobProgressInfo
import org.apache.linkis.rpc.Sender
import org.apache.linkis.scheduler.executer.ExecuteResponse

import scala.collection.JavaConversions.mapAsScalaMap

class PipelineEngineConnExecutor(val id: Int) extends ComputationExecutor with Logging {

  def getName: String = "pipeLineEngine"

  private var index = 0

  private var progressInfo: JobProgressInfo = _

  private val executorLabels: util.List[Label[_]] = new util.ArrayList[Label[_]]()




  override def executeLine(engineExecutorContext: EngineExecutionContext, code: String): ExecuteResponse = {
    index += 1
    var failedTasks = 0
    var succeedTasks = 1
    val newOptions = engineExecutorContext.getProperties
    newOptions.foreach({ case (k, v) => info(s"key is $k, value is $v") })
    val regex = "(?i)\\s*from\\s+(\\S+)\\s+to\\s+(\\S+)\\s?".r
    try {
      code match {
        case regex(sourcePath, destPath) => PipelineExecutorSelector.select(sourcePath,destPath,newOptions.asInstanceOf[util.Map[String, String]]).execute(sourcePath,destPath,engineExecutorContext)
        case _ => throw new PipeLineErrorException(70007, "非法的out脚本语句（Illegal out script statement）")
      }
    } catch {
      case e: Exception => failedTasks = 1; succeedTasks = 0; throw e
    }
    finally {
      info("begin to remove osCache:" + engineExecutorContext.getJobId.get)
      OutputStreamCache.osCache.remove(engineExecutorContext.getJobId.get)
      progressInfo = JobProgressInfo(getName + "_" + index, 1, 0, failedTasks, succeedTasks)
    }

  }

  override def executeCompletely(engineExecutorContext: EngineExecutionContext, code: String, completedLine: String): ExecuteResponse = null

  override def progress(taskID: String): Float = if (null == progressInfo) 0f else 1f

  override def getProgressInfo(taskID: String): Array[JobProgressInfo] = null

  override def supportCallBackLogs(): Boolean = true

  override def requestExpectedResource(expectedResource: NodeResource): NodeResource = {
    null
  }

  override def getCurrentNodeResource(): NodeResource = {
    // todo refactor for duplicate
    val properties = EngineConnObject.getEngineCreationContext.getOptions
    if (properties.containsKey(EngineConnPluginConf.JAVA_ENGINE_REQUEST_MEMORY.key)) {
      val settingClientMemory = properties.get(EngineConnPluginConf.JAVA_ENGINE_REQUEST_MEMORY.key)
      if (!settingClientMemory.toLowerCase().endsWith("g")) {
        properties.put(EngineConnPluginConf.JAVA_ENGINE_REQUEST_MEMORY.key, settingClientMemory + "g")
      }
    }
    val actualUsedResource = new LoadInstanceResource(EngineConnPluginConf.JAVA_ENGINE_REQUEST_MEMORY.getValue(properties).toLong,
      EngineConnPluginConf.JAVA_ENGINE_REQUEST_CORES.getValue(properties), EngineConnPluginConf.JAVA_ENGINE_REQUEST_INSTANCE)
    val resource = new CommonNodeResource
    resource.setUsedResource(actualUsedResource)
    resource
  }

  override def getExecutorLabels(): util.List[Label[_]] = executorLabels

  override def setExecutorLabels(labels: util.List[Label[_]]): Unit = {
    executorLabels.clear()
    executorLabels.addAll(labels)
  }

  override def getId(): String = Sender.getThisServiceInstance.getInstance + "_" + id

}

object PipelineEngineConnExecutor {

  val pipelineExecutors = Array(CopyExecutor.getInstance,CSVExecutor.getInstance,ExcelExecutor.getInstance)

  def listPipelineExecutors(): Array[PipeLineExecutor] = pipelineExecutors
}