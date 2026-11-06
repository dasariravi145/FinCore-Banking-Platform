output "jenkins_instance_id" {

        value = try(aws_instance.jenkins[0].id, null)
}
output "jenkins_private_ip" {

        value = try(aws_instance.jenkins[0].private_ip, null)
}
output "jenkins_agent_instance_id" {

        value = try(aws_instance.jenkins_agent[0].id, null)
}
output "jenkins_agent_private_ip" {

        value = try(aws_instance.jenkins_agent[0].private_ip, null)
}
output "sonar_instance_id" {

        value = try(aws_instance.sonarqube[0].id, null)
}
output "runner_instance_id" {
    
        value = try(aws_instance.runner[0].id, null)
}
