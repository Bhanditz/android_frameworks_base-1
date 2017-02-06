/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.view.autofill;

import static android.view.autofill.Helper.DEBUG;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArraySet;

import com.android.internal.util.Preconditions;

/**
 * Response for a {@link
 * android.service.autofill.AutoFillService#onFillRequest(android.app.assist.AssistStructure,
 * Bundle, android.os.CancellationSignal, android.service.autofill.FillCallback)} and
 * authentication requests.
 *
 * <p>The response typically contains one or more {@link Dataset}s, each representing a set of
 * fields that can be auto-filled together, and the Android system displays a dataset picker UI
 * affordance that the user must use before the {@link Activity} is filled with the dataset.
 *
 * <p>For example, for a login page with username/password where the user only has one account in
 * the response could be:
 *
 * <pre class="prettyprint">
 *  new FillResponse.Builder()
 *      .add(new Dataset.Builder("homer")
 *          .setTextFieldValue(id1, "homer")
 *          .setTextFieldValue(id2, "D'OH!")
 *          .build())
 *      .build();
 * </pre>
 *
 * <p>If the user had 2 accounts, each with its own user-provided names, the response could be:
 *
 * <pre class="prettyprint">
 *  new FillResponse.Builder()
 *      .add(new Dataset.Builder("Homer's Account")
 *          .setTextFieldValue(id1, "homer")
 *          .setTextFieldValue(id2, "D'OH!")
 *          .build())
 *      .add(new Dataset.Builder("Bart's Account")
 *          .setTextFieldValue(id1, "elbarto")
 *          .setTextFieldValue(id2, "cowabonga")
 *          .build())
 *      .build();
 * </pre>
 *
 * <p>If the user does not have any data associated with this {@link Activity} but the service wants
 * to offer the user the option to save the data that was entered, then the service could populate
 * the response with {@code savableIds} instead of {@link Dataset}s:
 *
 * <pre class="prettyprint">
 *  new FillResponse.Builder()
 *      .addSavableFields(id1, id2)
 *      .build();
 * </pre>
 *
 * <p>Similarly, there might be cases where the user data on the service is enough to populate some
 * fields but not all, and the service would still be interested on saving the other fields. In this
 * scenario, the service could populate the response with both {@link Dataset}s and {@code
 * savableIds}:
 *
 * <pre class="prettyprint">
 *   new FillResponse.Builder()
 *       .add(new Dataset.Builder("Homer")
 *          .setTextFieldValue(id1, "Homer")                  // first name
 *          .setTextFieldValue(id2, "Simpson")                // last name
 *          .setTextFieldValue(id3, "742 Evergreen Terrace")  // street
 *          .setTextFieldValue(id4, "Springfield")            // city
 *          .build())
 *       .addSavableFields(id5, id6) // state and zipcode
 *       .build();
 *
 * </pre>
 *
 * <p>Notice that the ids that are part of a dataset (ids 1 to 4, in this example) are automatically
 * added to the {@code savableIds} list.
 *
 * <p>If the service has multiple {@link Dataset}s for different sections of the activity,
 * for example, a user section for which there are two datasets followed by an address
 * section for which there are two datasets for each user user, then it should "partition"
 * the activity in sections and populate the response with just a subset of the data that would
 * fulfill the first section (the name in our example); then once the user fills the first
 * section and taps a field from the next section (the address in our example), the Android
 * system would issue another request for that section, and so on. Note that if the user
 * chooses to populate the first section with a service provided dataset, the subsequent request
 * would contain the populated values so you don't try to provide suggestions for the first
 * section but ony for the second one based on the context of what was already filled. For
 * example, the first response could be:
 *
 * <pre class="prettyprint">
 *  new FillResponse.Builder()
 *      .add(new Dataset.Builder("Homer")
 *          .setTextFieldValue(id1, "Homer")
 *          .setTextFieldValue(id2, "Simpson")
 *          .build())
 *      .add(new Dataset.Builder("Bart")
 *          .setTextFieldValue(id1, "Bart")
 *          .setTextFieldValue(id2, "Simpson")
 *          .build())
 *      .build();
 * </pre>
 *
 * <p>Then after the user picks the {@code Homer} dataset and taps the {@code Street} field to
 * trigger another auto-fill request, the second response could be:
 *
 * <pre class="prettyprint">
 *  new FillResponse.Builder()
 *      .add(new Dataset.Builder("Home")
 *          .setTextFieldValue(id3, "742 Evergreen Terrace")
 *          .setTextFieldValue(id4, "Springfield")
 *          .build())
 *      .add(new Dataset.Builder("Work")
 *          .setTextFieldValue(id3, "Springfield Power Plant")
 *          .setTextFieldValue(id4, "Springfield")
 *          .build())
 *      .build();
 * </pre>
 *
 * <p>The service could require user authentication at the {@link FillResponse} or the
 * {@link Dataset} level, prior to auto-filling an activity - see {@link FillResponse.Builder
 * #setAuthentication(IntentSender)} and {@link Dataset.Builder#setAuthentication(IntentSender)}.
 * It is recommended that you encrypt only the sensitive data but leave the labels unencrypted
 * which would allow you to provide the dataset names to the user and if they choose one
 * them challenge the user to authenticate. For example, if the user has a home and a work
 * address the Home and Work labels could be stored unencrypted as they don't have any sensitive
 * data while the address data is in an encrypted storage. If the user chooses Home, then the
 * platform will start your authentication flow. If you encrypt all data and require auth
 * at the response level the user will have to interact with the fill UI to trigger a request
 * for the datasets as they don't see Home and Work options which will trigger your auth
 * flow and after successfully authenticating the user will be presented with the Home and
 * Work options where they can pick one. Hence, you have flexibility how to implement your
 * auth while storing labels non-encrypted and data encrypted provides a better user
 * experience.</p>
 *
 * <p>Finally, the service can use {@link Dataset.Builder#setExtras(Bundle)} methods
 * to pass {@link Bundle extras} provided to all future calls related to a dataset,
 * for example during authentication and saving.</p>
 */
public final class FillResponse implements Parcelable {
    private final String mId;
    private final ArraySet<Dataset> mDatasets;
    private final ArraySet<AutoFillId> mSavableIds;
    private final Bundle mExtras;
    private final IntentSender mAuthentication;

    private FillResponse(@NonNull Builder builder) {
        mId = builder.mId;
        mDatasets = builder.mDatasets;
        mSavableIds = builder.mSavableIds;
        mExtras = builder.mExtras;
        mAuthentication = builder.mAuthentication;
    }

    /** @hide */
    public @NonNull String getId() {
        return mId;
    }

    /** @hide */
    public @Nullable Bundle getExtras() {
        return mExtras;
    }

    /** @hide */
    public @Nullable ArraySet<Dataset> getDatasets() {
        return mDatasets;
    }

    /** @hide */
    public @Nullable ArraySet<AutoFillId> getSavableIds() {
        return mSavableIds;
    }

    /** @hide */
    public @Nullable IntentSender getAuthentication() {
        return mAuthentication;
    }

    /**
     * Builder for {@link FillResponse} objects. You must to provide at least
     * one dataset or set an authentication intent.
     */
    public static final class Builder {
        private final String mId;
        private ArraySet<Dataset> mDatasets;
        private ArraySet<AutoFillId> mSavableIds;
        private Bundle mExtras;
        private IntentSender mAuthentication;
        private boolean mDestroyed;

        /** @hide */
        // TODO(b/33197203): Remove once GCore migrates
        public Builder() {
            this(String.valueOf(System.currentTimeMillis()));
        }

        /**
         * Creates a new {@link FillResponse} builder.
         *
         * @param id A required id to identify this dataset for future interactions related to it.
         */
        public Builder(@NonNull String id) {
            mId = Preconditions.checkStringNotEmpty(id, "id cannot be empty or null");
        }

        /**
         * Requires a fill response authentication before auto-filling the activity with
         * any dataset in this response. This is typically useful when a user interaction
         * is required to unlock their data vault if you encrypt the dataset labels and
         * dataset data. It is recommended to encrypt only the sensitive data and not the
         * dataset labels which would allow auth on the dataset level leading to a better
         * user experience. Note that if you use sensitive data as a label, for example an
         * email address, then it should also be encrypted.
         *
         * <p>This method is called when you need to provide an authentication
         * UI for the fill response. For example, when the user's data is stored
         * encrypted and needs a user interaction to decrypt before offering fill
         * suggestions.</p>
         *
         * <p>When a user initiates an auto fill, the system triggers the provided
         * intent whose extras will have the {@link android.content.Intent
         * #EXTRA_AUTO_FILL_ITEM_ID id} of the {@link android.view.autofill.FillResponse})
         * to authenticate, the {@link android.content.Intent#EXTRA_AUTO_FILL_EXTRAS extras}
         * associated with this response, and a {@link android.content.Intent
         * #EXTRA_AUTO_FILL_CALLBACK callback} to dispatch the authentication result.</p>
         *
         * <p>Once you complete your authentication flow you should use the provided callback
         * to notify for a failure or a success. In case of a success you need to provide
         * the fully populated response that is being authenticated. For example, if you
         * provided an empty {@link FillResponse} because the user's data was locked and
         * marked that the response needs an authentication then in the response returned
         * if authentication succeeds you need to provide all available datasets some of
         * which may need to be further authenticated, for example a credit card whose
         * CVV needs to be entered.</p>
         *
         * <p>The indent sender mechanism allows you to have your authentication UI
         * implemented as an activity or a service or a receiver. However, the recommended
         * way is to do this is with an activity which the system will start in the
         * filled activity's task meaning it will properly work with back, recent apps, and
         * free-form multi-window, while avoiding the need for the "draw on top of other"
         * apps special permission. You can still theme your authentication activity's
         * UI to look like a dialog if desired.</p>
         *
         * <p></><strong>Note:</strong> Do not make the provided intent sender
         * immutable by using {@link android.app.PendingIntent#FLAG_IMMUTABLE} as the
         * platform needs to fill in the authentication arguments.</p>
         *
         * @param authentication Intent to trigger your authentication flow.
         *
         * @see android.app.PendingIntent#getIntentSender()
         */
        public @NonNull Builder setAuthentication(@Nullable IntentSender authentication) {
            throwIfDestroyed();
            mAuthentication = authentication;
            return this;
        }

        /**
         * Adds a new {@link Dataset} to this response. Adding a dataset with the
         * same id updates the existing one.
         *
         * @throws IllegalArgumentException if a dataset with same {@code name} already exists.
         */
        public@NonNull Builder addDataset(@Nullable Dataset dataset) {
            throwIfDestroyed();
            if (dataset == null) {
                return this;
            }
            if (mDatasets == null) {
                mDatasets = new ArraySet<>();
            }
            final int datasetCount = mDatasets.size();
            for (int i = 0; i < datasetCount; i++) {
                if (mDatasets.valueAt(i).getName().equals(dataset.getName())) {
                    throw new IllegalArgumentException("Duplicate dataset name: "
                            + dataset.getName());
                }
            }
            if (!mDatasets.add(dataset)) {
                return this;
            }
            final int fieldCount = dataset.getFieldIds().size();
            for (int i = 0; i < fieldCount; i++) {
                final AutoFillId id = dataset.getFieldIds().get(i);
                if (mSavableIds == null) {
                    mSavableIds = new ArraySet<>();
                }
                mSavableIds.add(id);
            }
            return this;
        }

        /**
         * Adds ids of additional fields that the service would be interested to save (through
         * {@link android.service.autofill.AutoFillService#onSaveRequest(
         * android.app.assist.AssistStructure, Bundle, android.service.autofill.SaveCallback)})
         * but were not indirectly set through {@link #addDataset(Dataset)}.
         *
         * <p>See {@link FillResponse} for examples.
         */
        public @NonNull Builder addSavableFields(@Nullable AutoFillId... ids) {
            throwIfDestroyed();
            if (ids == null) {
                return this;
            }
            for (AutoFillId id : ids) {
                if (mSavableIds == null) {
                    mSavableIds = new ArraySet<>();
                }
                mSavableIds.add(id);
            }
            return this;
        }

        /**
         * Sets a {@link Bundle} that will be passed to subsequent APIs that
         * manipulate this response. For example, they are passed in as {@link
         * android.content.Intent#EXTRA_AUTO_FILL_EXTRAS extras} to your
         * authentication flow and to subsequent calls to {@link
         * android.service.autofill.AutoFillService#onFillRequest(
         * android.app.assist.AssistStructure, Bundle, android.os.CancellationSignal,
         * android.service.autofill.FillCallback)} and {@link
         * android.service.autofill.AutoFillService#onSaveRequest(
         * android.app.assist.AssistStructure, Bundle,
         * android.service.autofill.SaveCallback)}.
         */
        public Builder setExtras(Bundle extras) {
            throwIfDestroyed();
            mExtras = extras;
            return this;
        }

        /**
         * Builds a new {@link FillResponse} instance.
         */
        public FillResponse build() {
            throwIfDestroyed();
            mDestroyed = true;
            return new FillResponse(this);
        }

        private void throwIfDestroyed() {
            if (mDestroyed) {
                throw new IllegalStateException("Already called #build()");
            }
        }
    }

    /////////////////////////////////////
    //  Object "contract" methods. //
    /////////////////////////////////////
    @Override
    public String toString() {
        if (!DEBUG) return super.toString();
        final StringBuilder builder = new StringBuilder(
                "FillResponse: [id=").append(mId)
                .append(", datasets=").append(mDatasets)
                .append(", savableIds=").append(mSavableIds)
                .append(", hasExtras=").append(mExtras != null)
                .append(", hasAuthentication=").append(mAuthentication != null);
        return builder.append(']').toString();
    }

    /////////////////////////////////////
    //  Parcelable "contract" methods. //
    /////////////////////////////////////

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mId);
        parcel.writeTypedArraySet(mDatasets, 0);
        parcel.writeTypedArraySet(mSavableIds, 0);
        parcel.writeParcelable(mExtras, 0);
        parcel.writeParcelable(mAuthentication, 0);
    }

    public static final Parcelable.Creator<FillResponse> CREATOR =
            new Parcelable.Creator<FillResponse>() {
        @Override
        public FillResponse createFromParcel(Parcel parcel) {
            // Always go through the builder to ensure the data ingested by
            // the system obeys the contract of the builder to avoid attacks
            // using specially crafted parcels.
            final Builder builder = new Builder(parcel.readString());
            final ArraySet<Dataset> datasets = parcel.readTypedArraySet(null);
            final int datasetCount = (datasets != null) ? datasets.size() : 0;
            for (int i = 0; i < datasetCount; i++) {
                builder.addDataset(datasets.valueAt(i));
            }
            final ArraySet<AutoFillId> fillIds = parcel.readTypedArraySet(null);
            final int fillIdCount = (fillIds != null) ? fillIds.size() : 0;
            for (int i = 0; i < fillIdCount; i++) {
                builder.addSavableFields(fillIds.valueAt(i));
            }
            builder.setExtras(parcel.readParcelable(null));
            builder.setAuthentication(parcel.readParcelable(null));
            return builder.build();
        }

        @Override
        public FillResponse[] newArray(int size) {
            return new FillResponse[size];
        }
    };
}
