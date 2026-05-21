variable "project_name" {
  description = "Project name used as resource prefix."
  type        = string
}

variable "compartment_id" {
  description = "OCI compartment OCID."
  type        = string
}

variable "availability_domain" {
  description = "Availability domain where the MySQL DB System primary endpoint will be created."
  type        = string
}

variable "subnet_id" {
  description = "Subnet OCID used by the MySQL DB System endpoint."
  type        = string
}

variable "nlb_subnet_id" {
  description = "Public subnet OCID used by the Network Load Balancer that exposes MySQL publicly for demo."
  type        = string
}

variable "mysql_admin_username" {
  description = "Administrative username for MySQL HeatWave."
  type        = string
  default     = "admin"
}

variable "mysql_admin_password" {
  description = "Administrative password for MySQL HeatWave."
  type        = string
  sensitive   = true
}

variable "mysql_shape_name" {
  description = "MySQL DB System shape. ECPU shapes are recommended."
  type        = string
  default     = "MySQL.2"
}

variable "mysql_data_storage_size_in_gb" {
  description = "Initial MySQL DB System data storage size in GB."
  type        = number
  default     = 50
}

variable "mysql_auto_expand_storage" {
  description = "Enable automatic storage expansion."
  type        = bool
  default     = true
}

variable "mysql_max_storage_size_in_gbs" {
  description = "Maximum storage size when automatic expansion is enabled."
  type        = number
  default     = 100
}

variable "mysql_port" {
  description = "MySQL TCP port."
  type        = number
  default     = 3306
}

variable "mysql_port_x" {
  description = "MySQL X Protocol port."
  type        = number
  default     = 33060
}

variable "mysql_database_name" {
  description = "Application database name used to generate JDBC outputs."
  type        = string
  default     = "copa_ticketing_demo"
}

variable "mysql_enable_heatwave_cluster" {
  description = "Whether to attach a HeatWave cluster to the MySQL DB System."
  type        = bool
  default     = true
}

variable "mysql_heatwave_shape_name" {
  description = "HeatWave cluster shape."
  type        = string
  default     = "HeatWave.32GB"
}

variable "mysql_heatwave_cluster_size" {
  description = "Number of HeatWave cluster nodes."
  type        = number
  default     = 1
}

variable "freeform_tags" {
  description = "Freeform tags applied to resources."
  type        = map(string)
  default     = {}
}