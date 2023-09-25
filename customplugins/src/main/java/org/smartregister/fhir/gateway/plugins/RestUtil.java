/*
 * Copyright 2021-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartregister.fhir.gateway.plugins;

import com.google.fhir.gateway.ResourceFinderImp;

public class RestUtil {

  private static ResourceFinderImp instance = null;

  //    private final FhirContext fhirContext;
  //
  //    public static synchronized ResourceFinderImp getInstance(FhirContext fhirContext) {
  //        if (instance != null) {
  //            return instance;
  //        } else {
  //            instance = new ResourceFinderImp(fhirContext);
  //            return instance;
  //        }
  //    }
}
