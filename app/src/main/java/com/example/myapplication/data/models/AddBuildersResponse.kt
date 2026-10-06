package com.example.myapplication.data.models

import com.google.gson.annotations.SerializedName

data class AddBuildersResponse(
    @SerializedName("added") val added: List<ProjectMemberDto> = emptyList(),
    @SerializedName("notFound") val notFound: List<String> = emptyList(),
    @SerializedName("alreadyMembers") val alreadyMembers: List<String> = emptyList(),
    @SerializedName("addedCount") val addedCount: Int = 0,
    @SerializedName("notFoundCount") val notFoundCount: Int = 0,
    @SerializedName("alreadyCount") val alreadyCount: Int = 0
)